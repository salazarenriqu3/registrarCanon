package com.iuims.registrar.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.security.access.AccessDeniedException;

@ControllerAdvice(annotations = Controller.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RegistrarWebExceptionHandler {

    @ExceptionHandler({
        IllegalStateException.class,
        IllegalArgumentException.class,
        NoSuchElementException.class,
        BindException.class,
        MethodArgumentNotValidException.class,
        MethodArgumentTypeMismatchException.class,
        AccessDeniedException.class,
        ResponseStatusException.class
    })
    public Object handleKnown(Exception ex, HttpServletRequest request) {
        int status = resolveStatus(ex);
        String message = resolveMessage(ex, status);
        return render(request, status, message);
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HttpServletRequest request) {
        return render(request, HttpStatus.INTERNAL_SERVER_ERROR.value(), safeMessage(ex));
    }

    private Object render(HttpServletRequest request, int status, String message) {
        if (wantsJson(request)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", status);
            body.put("error", HttpStatus.resolve(status) != null
                ? HttpStatus.resolve(status).getReasonPhrase()
                : "Registrar Error");
            body.put("message", message);
            body.put("path", request.getRequestURI());
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
        }

        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatusCodeAdapter.resolve(status));
        mav.addObject("status", status);
        mav.addObject("statusText", HttpStatus.resolve(status) != null
            ? HttpStatus.resolve(status).getReasonPhrase()
            : "Registrar Error");
        mav.addObject("message", message);
        mav.addObject("path", request.getRequestURI());
        mav.addObject("backUrl", resolveBackUrl(request));
        mav.addObject("timestamp", java.time.OffsetDateTime.now(java.time.ZoneId.systemDefault()));
        return mav;
    }

    private boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private int resolveStatus(Exception ex) {
        if (ex instanceof ResponseStatusException rse) {
            return rse.getStatusCode().value();
        }
        if (ex instanceof AccessDeniedException) {
            return HttpStatus.FORBIDDEN.value();
        }
        if (ex instanceof NoSuchElementException) {
            return HttpStatus.NOT_FOUND.value();
        }
        if (ex instanceof BindException || ex instanceof MethodArgumentNotValidException || ex instanceof MethodArgumentTypeMismatchException || ex instanceof IllegalArgumentException) {
            return HttpStatus.BAD_REQUEST.value();
        }
        if (ex instanceof IllegalStateException) {
            return HttpStatus.CONFLICT.value();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private String resolveMessage(Exception ex, int status) {
        if (ex instanceof ResponseStatusException rse && rse.getReason() != null && !rse.getReason().isBlank()) {
            return rse.getReason();
        }
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            return ex.getMessage();
        }
        return switch (status) {
            case 400 -> "The registrar could not process the submitted data.";
            case 403 -> "You do not have permission to access this registrar action.";
            case 404 -> "The registrar page or record could not be found.";
            case 409 -> "The registrar action conflicts with the current record state.";
            default -> "The registrar encountered an unexpected error.";
        };
    }

    private String safeMessage(Exception ex) {
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            return ex.getMessage();
        }
        return "The registrar encountered an unexpected error.";
    }

    private String resolveBackUrl(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        String contextPath = request.getContextPath() != null ? request.getContextPath() : "";
        if (referer != null && !referer.isBlank() && !referer.contains("/error")) {
            return referer;
        }
        return contextPath.isBlank() ? "/" : contextPath + "/";
    }

    private static final class HttpStatusCodeAdapter {
        private static org.springframework.http.HttpStatusCode resolve(int status) {
            HttpStatus httpStatus = HttpStatus.resolve(status);
            return httpStatus != null ? httpStatus : HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
