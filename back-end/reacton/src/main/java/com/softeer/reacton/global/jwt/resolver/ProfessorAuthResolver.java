package com.softeer.reacton.global.jwt.resolver;

import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.JwtErrorCode;
import com.softeer.reacton.global.jwt.util.JwtTokenUtil;
import com.softeer.reacton.global.jwt.dto.ProfessorAuthInfo;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProfessorAuthResolver implements HandlerMethodArgumentResolver {

    private final JwtTokenUtil jwtTokenUtil;

    private static final String PROFESSOR_TOKEN_COOKIE_NAME = "access_token";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(ProfessorAuthInfo.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        String token = extractTokenFromRequest(request);

        if (token == null) {
            throw new BaseException(JwtErrorCode.ACCESS_TOKEN_ERROR);
        }

        Claims claims = jwtTokenUtil.getClaims(token);
        Integer professorIdInt = claims.get("professorId", Integer.class);
        if (professorIdInt == null) {
            throw new BaseException(JwtErrorCode.ACCESS_TOKEN_ERROR);
        }

        Long professorId = professorIdInt.longValue();
        return new ProfessorAuthInfo(professorId);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (PROFESSOR_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
