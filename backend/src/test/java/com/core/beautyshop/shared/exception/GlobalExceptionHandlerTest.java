package com.core.beautyshop.shared.exception;

import com.core.beautyshop.shared.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExceptionHandlerTest {

    @Test
    void validationErrorsNeverEchoRejectedPassword() throws Exception {
        var input = new com.core.beautyshop.modules.identity.application.dto.request.RegisterRequest();
        input.setPassword("private-value");
        var binding = new org.springframework.validation.BeanPropertyBindingResult(input, "registerRequest");
        binding.addError(new org.springframework.validation.FieldError("registerRequest", "password",
                "private-value", false, null, null, "Invalid length"));
        var parameter = new org.springframework.core.MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("receive", Object.class), 0);
        var response = new GlobalExceptionHandler().handleValidationExceptions(
                new org.springframework.web.bind.MethodArgumentNotValidException(parameter, binding));
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(response.getBody())
                .contains("private-value"));
        assertFalse(input.toString().contains("private-value"));
    }

    private void receive(Object input) { }

    @Test
    void genericErrorDoesNotExposeInternalExceptionDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(
                new RuntimeException("jdbc:mysql://db.internal:3306 secret-password"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse(response.getBody().getMessage().contains("db.internal"));
        assertNull(response.getBody().getErrors());
    }
}
