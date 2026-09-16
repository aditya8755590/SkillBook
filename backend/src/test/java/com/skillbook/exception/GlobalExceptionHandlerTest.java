package com.skillbook.exception;

import com.skillbook.common.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void validationsErrorsReturn400WithFieldMap() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(
                List.of(new FieldError("obj", "email", "must not be blank"),
                        new FieldError("obj", "password", "size must be between 8 and 100"))
        );

        ResponseEntity<ApiResponse<Map<String, String>>> response = handler.handleValidationErrors(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        ApiResponse<Map<String, String>> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getError()).isEqualTo("Validation failed");
        assertThat(body.getData()).containsEntry("email", "must not be blank");
    }

    @Test
    void illegalArgumentReturns400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("Bad input"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Bad input");
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    void illegalStateReturns409() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleIllegalState(new IllegalStateException("Slot already booked"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Slot already booked");
    }

    @Test
    void resourceNotFoundReturns404() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotFound(new ResourceNotFoundException("User not found"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("User not found");
    }

    @Test
    void genericExceptionReturns500WithSafeMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGenericException(new RuntimeException("internal details that must not leak"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError())
                .isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getError()).doesNotContain("internal details");
    }
}