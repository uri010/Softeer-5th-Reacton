package com.softeer.reacton.global.jwt;

import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.JwtErrorCode;
import com.softeer.reacton.global.jwt.dto.SignupTokenInfo;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class SignupTokenArgumentResolver implements HandlerMethodArgumentResolver {

    private final JwtTokenUtil jwtTokenUtil;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(SignupTokenInfo.class);
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
        String oauthId = claims.get("oauthId", String.class);
        String email = claims.get("email", String.class);
        Boolean isSignedUp = claims.get("isSignedUp", Boolean.class);

        if (oauthId == null || email == null || isSignedUp == null) {
            throw new BaseException(JwtErrorCode.ACCESS_TOKEN_ERROR);
        }

        return new SignupTokenInfo(oauthId, email, isSignedUp);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("signup_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
