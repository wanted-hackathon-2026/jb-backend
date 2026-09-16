package com.jachwibangjeongsig.jb.me.exception;
import com.jachwibangjeongsig.jb.auth.exception.AuthProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages = {"com.jachwibangjeongsig.jb.me.controller", "com.jachwibangjeongsig.jb.favorite.controller"})
public class AccountApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<java.util.Map<String, Object>> api(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(AuthProblemDetails.response(exception.status(), exception.code(), exception.getMessage(), request));
    }
}
