package com.iuims.registrar.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Controller
public class RegistrarErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        int status = getStatus(request);
        String path = getString(request, RequestDispatcher.ERROR_REQUEST_URI);
        String message = getString(request, RequestDispatcher.ERROR_MESSAGE);
        String exception = getExceptionMessage(request);
        String detail = firstNonBlank(message, exception, defaultMessage(status));

        model.addAttribute("status", status);
        model.addAttribute("statusText", statusText(status));
        model.addAttribute("message", detail);
        model.addAttribute("path", path != null ? path : "(unknown)");
        model.addAttribute("timestamp", OffsetDateTime.now(ZoneId.systemDefault()));
        model.addAttribute("backUrl", resolveBackUrl(request, path));
        return "error";
    }

    private int getStatus(HttpServletRequest request) {
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusObj instanceof Integer i) {
            return i;
        }
        if (statusObj instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private String getString(HttpServletRequest request, String key) {
        Object value = request.getAttribute(key);
        return value != null ? value.toString() : null;
    }

    private String getExceptionMessage(HttpServletRequest request) {
        Object throwable = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (throwable instanceof Throwable t && t.getMessage() != null && !t.getMessage().isBlank()) {
            return t.getMessage();
        }
        return null;
    }

    private String statusText(int status) {
        HttpStatus httpStatus = HttpStatus.resolve(status);
        return httpStatus != null ? httpStatus.getReasonPhrase() : "Registrar Error";
    }

    private String defaultMessage(int status) {
        return switch (status) {
            case 400 -> "We could not complete your request because the submitted data was not valid.";
            case 403 -> "You do not have permission to access this registrar page.";
            case 404 -> "The registrar page you tried to open does not exist.";
            case 409 -> "The requested registrar action conflicts with the current record state.";
            default -> "The registrar encountered an unexpected problem. Please go back and try again.";
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String resolveBackUrl(HttpServletRequest request, String path) {
        String referer = request.getHeader("Referer");
        String contextPath = request.getContextPath() != null ? request.getContextPath() : "";
        String fallback = contextPath.isBlank() ? "/" : contextPath + "/";
        if (referer != null && !referer.isBlank()) {
            if (path == null || !referer.contains(path)) {
                return referer;
            }
        }
        return fallback;
    }
}
