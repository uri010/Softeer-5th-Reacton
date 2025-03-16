package com.softeer.reacton.global.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.dto.ExceptionResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;

    private static final String PROFESSOR_COOKIE_NAME = "access_token";
    private static final String STUDENT_COOKIE_NAME = "student_access_token";

    private static final List<String> WHITE_LIST_URLS = List.of(
            "/auth/google/url",
            "/auth/google/callback",
            "/swagger-ui",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/students/courses"
    );

    private static final List<String> STUDENT_ACCESS_URLS = List.of(
            "/students/questions",
            "/students/requests",
            "/students/reactions"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        String requestMethod = request.getMethod();

        log.info("[JWT Filter Start] requestUri = {}, method = {}", requestUri, requestMethod);

        if( requestMethod.equals("OPTIONS")) {
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
            if ((isStudentRequest(requestUri))) {
                filterStudent(request);
            } else {
                filterProfessor(request);
            }

            chain.doFilter(request, response);
        } catch (BaseException e) {
            setErrorResponse(response, e);
        }
    }

    private void filterStudent(HttpServletRequest request) {
        String token = getStudentJwtFromCookie(request);
        jwtTokenUtil.validateToken(token);
        Map<String, Object> userInfo = jwtTokenUtil.getStudentInfoFromToken(token);

        request.setAttribute("studentId", userInfo.get("studentId"));
        request.setAttribute("courseId", userInfo.get("courseId"));

        log.info("[JWT Validation Success] Student: studentId = {}, courseId = {}", userInfo.get("studentId"), userInfo.get("courseId"));
    }

    private void filterProfessor(HttpServletRequest request) {
        String token = getProfessorJwtFromCookie(request);
        jwtTokenUtil.validateToken(token);
        Map<String, Object> userInfo = jwtTokenUtil.getProfessorInfoFromToken(token);

        request.setAttribute("oauthId", userInfo.get("oauthId"));
        request.setAttribute("email", userInfo.get("email"));
        request.setAttribute("isSignedUp", userInfo.get("isSignedUp"));

        log.info("[JWT Validation Success] Professor: email = {}, oauthId = {}", userInfo.get("email"), userInfo.get("oauthId"));
    }

    private boolean isWhiteListed(String requestUri) {
        return WHITE_LIST_URLS.stream().anyMatch(requestUri::startsWith);
    }

    private String getProfessorJwtFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            log.warn("[JWT Missing] No professor cookie found.");
            return null;
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> PROFESSOR_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String getStudentJwtFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            log.warn("[JWT Missing] No student cookie found.");
            return null;
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> STUDENT_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean isStudentRequest(String requestUri) {
        return STUDENT_ACCESS_URLS.stream().anyMatch(requestUri::startsWith);
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