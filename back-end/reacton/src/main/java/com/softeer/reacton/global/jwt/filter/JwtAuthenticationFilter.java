package com.softeer.reacton.global.jwt.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.dto.ExceptionResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> WHITE_LIST_URLS = List.of(
            "/auth/google/url",
            "/auth/google/callback",
            "/swagger-ui",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/students/courses"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        String requestMethod = request.getMethod();

        log.info("[JWT Filter Start] requestUri = {}, method = {}", requestUri, requestMethod);

        if (requestMethod.equals("OPTIONS")) {
            log.info("[JWT Filter Skipped] OPTIONS method detected.");
            chain.doFilter(request, response);
            return;
        }

        if (isWhiteListed(requestUri)) {
            log.info("[JWT Filter Skipped] Whitelisted URL detected: requestUri = {}", requestUri);
            chain.doFilter(request, response);
            return;
        }

        try {
            chain.doFilter(request, response);
        } catch (BaseException e) {
            setErrorResponse(response, e);
        }
    }

    private boolean isWhiteListed(String requestUri) {
        return WHITE_LIST_URLS.stream().anyMatch(requestUri::startsWith);
    }

    private void setErrorResponse(HttpServletResponse response, BaseException e) throws IOException {
        log.error("[JWT Validation Failed] error = {}", e.getErrorCode().getMessage());

        ObjectMapper objectMapper = new ObjectMapper();

        response.setContentType("application/json");
        response.setStatus(e.getErrorCode().getStatus().value());

        ExceptionResponse exceptionResponse = ExceptionResponse.of(e.getErrorCode());

        objectMapper.writeValue(response.getOutputStream(), exceptionResponse);
    }
}