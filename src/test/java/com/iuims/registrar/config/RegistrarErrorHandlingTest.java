package com.iuims.registrar.config;
import com.iuims.registrar.entity.Course;
import com.iuims.registrar.entity.Program;
import com.iuims.registrar.entity.Student;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ConcurrentModel;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrarErrorHandlingTest {

    private final RegistrarErrorController errorController = new RegistrarErrorController();
    private final RegistrarWebExceptionHandler exceptionHandler = new RegistrarWebExceptionHandler();

    @Test
    void errorControllerBuildsFriendlyErrorModel() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/registrar/admin/student-manager/shift-program");
        request.setContextPath("/registrar");
        request.setAttribute("jakarta.servlet.error.status_code", 500);
        request.setAttribute("jakarta.servlet.error.message", "Internal Server Error");
        request.addHeader("Referer", "http://localhost:8083/registrar/admin/student-manager");

        ConcurrentModel model = new ConcurrentModel();
        String view = errorController.handleError(request, model);

        assertThat(view).isEqualTo("error");
        assertThat(model.getAttribute("status")).isEqualTo(500);
        assertThat(model.getAttribute("statusText")).isEqualTo("Internal Server Error");
        assertThat(model.getAttribute("backUrl")).isEqualTo("http://localhost:8083/registrar/admin/student-manager");
    }

    @Test
    void exceptionHandlerTurnsStateErrorsIntoConflictModel() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/registrar/admin/curriculum");
        request.setContextPath("/registrar");

        Object result = exceptionHandler.handleKnown(new IllegalStateException("Course already exists"), request);

        assertThat(result).isInstanceOfAny(org.springframework.web.servlet.ModelAndView.class);
        org.springframework.web.servlet.ModelAndView mav = (org.springframework.web.servlet.ModelAndView) result;
        assertThat(mav.getViewName()).isEqualTo("error");
        assertThat(mav.getStatus()).isNotNull();
        assertThat(mav.getStatus().value()).isEqualTo(409);
        assertThat(mav.getModel().get("message")).isEqualTo("Course already exists");
    }

    @Test
    void exceptionHandlerCanReturnJsonWhenRequested() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/registrar/api/test");
        request.addHeader("Accept", "application/json");

        Object result = exceptionHandler.handleKnown(new IllegalArgumentException("Missing"), request);

        assertThat(result).isInstanceOf(org.springframework.http.ResponseEntity.class);
        org.springframework.http.ResponseEntity<?> response = (org.springframework.http.ResponseEntity<?>) result;
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}
