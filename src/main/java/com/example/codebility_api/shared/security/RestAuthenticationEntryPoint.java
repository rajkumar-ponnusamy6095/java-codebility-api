package com.example.codebility_api.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log =
            LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final SecurityErrorResponseWriter errorResponseWriter;

    public RestAuthenticationEntryPoint(
            SecurityErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        String message = authException instanceof InsufficientAuthenticationException
                ? "Authentication is required to access this resource"
                : authException.getMessage();

        log.warn("Unauthenticated {} {} -> 401: {}",
                request.getMethod(), request.getRequestURI(), message);

        errorResponseWriter.write(
                request, response, HttpStatus.UNAUTHORIZED, message
        );
    }
}
