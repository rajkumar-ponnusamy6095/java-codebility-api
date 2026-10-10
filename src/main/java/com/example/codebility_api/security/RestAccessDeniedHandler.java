package com.example.codebility_api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log =
            LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final SecurityErrorResponseWriter errorResponseWriter;

    public RestAccessDeniedHandler(
            SecurityErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        log.warn("Access denied {} {} -> 403 for principal={} authorities={}: {}",
                request.getMethod(),
                request.getRequestURI(),
                authentication != null ? authentication.getName() : null,
                authentication != null ? authentication.getAuthorities() : null,
                accessDeniedException.getMessage());

        errorResponseWriter.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "Insufficient role to access this resource"
        );
    }
}
