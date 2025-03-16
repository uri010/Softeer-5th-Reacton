package com.softeer.reacton.global.jwt;

import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.JwtErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
public class JwtTokenUtil {

    private final Key secretKey;
    private final long authTokenExpiration;
    private final long signUpTokenExpiration;
    private final long studentAccessTokenExpiration;

    public JwtTokenUtil(
            @Value("${jwt.secret-key}") String secretKey,
            @Value("${jwt.auth-access-token.expiration}") long authTokenExpiration,
            @Value("${jwt.signup-access-token.expiration}") long signUpTokenExpiration,
            @Value("${jwt.student-access-token.expiration}") long studentAccessTokenExpiration) {

        String encodedKey = Base64.getEncoder().encodeToString(secretKey.getBytes(StandardCharsets.UTF_8));
        this.secretKey = Keys.hmacShaKeyFor(encodedKey.getBytes());
        this.authTokenExpiration = authTokenExpiration;
        this.signUpTokenExpiration = signUpTokenExpiration;
        this.studentAccessTokenExpiration = studentAccessTokenExpiration;
    }

    public String createAuthAccessToken(String oauthId, String email) {
        log.info("[JWT Created] Auth Access Token: email = {}", email);

        return Jwts.builder()
                .claim("oauthId", oauthId)
                .claim("email", email)
                .claim("isSignedUp", true)
                .setExpiration(new Date(System.currentTimeMillis() + authTokenExpiration))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createSignUpToken(String oauthId, String email) {
        log.info("[JWT Created] SignUp Token: email = {}", email);

        return Jwts.builder()
                .claim("oauthId", oauthId)
                .claim("email", email)
                .claim("isSignedUp", false)
                .setExpiration(new Date(System.currentTimeMillis() + signUpTokenExpiration))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String createStudentAccessToken(String studentId, Long courseId) {
        log.info("[JWT Created] Student Access Token: studentId = {}, courseId = {}", studentId, courseId);

        return Jwts.builder()
                .claim("studentId", studentId)
                .claim("courseId", courseId)
                .setExpiration(new Date(System.currentTimeMillis() + studentAccessTokenExpiration))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public void validateToken(String token) {
        log.info("[JWT Validation] Start");

        if (token == null || token.isBlank()) {
            throw new BaseException(JwtErrorCode.ACCESS_TOKEN_ERROR);
        }

        try {
            Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token);
        } catch (RuntimeException e) {
            throw new BaseException(JwtErrorCode.ACCESS_TOKEN_ERROR);
        }

        log.info("[JWT Validation] Success");
    }

    public Map<String, Object> getProfessorInfoFromToken(String token) {
        log.info("[Extract JWT] Professor Info");

        Claims claims = getClaims(token);

        String oauthId = claims.get("oauthId", String.class);
        String email = claims.get("email", String.class);
        Boolean isSignedUp = claims.get("isSignedUp", Boolean.class);

        if (oauthId == null || email == null || isSignedUp == null) {
            throw new BaseException(JwtErrorCode.ACCESS_TOKEN_ERROR);
        }

        log.info("[Extract JWT Success] Professor: oauthId = {}, email = {}, isSignedUp = {}", oauthId, email, isSignedUp);

        return Map.of(
                "oauthId", oauthId,
                "email", email,
                "isSignedUp", isSignedUp
        );
    }

    public Map<String, Object> getStudentInfoFromToken(String token) {
        log.info("[Extract JWT] Student Info");

        Claims claims = getClaims(token);

        String studentId = claims.get("studentId", String.class);
        Long courseId = claims.get("courseId", Long.class);

        if (studentId == null || courseId == null) {
            throw new BaseException(JwtErrorCode.ACCESS_TOKEN_ERROR);
        }

        log.info("[Extract JWT Success] Student: studentId = {}, courseId = {}", studentId, courseId);

        return Map.of(
                "studentId", studentId,
                "courseId", courseId
        );
    }

    private Claims getClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            throw new BaseException(JwtErrorCode.ACCESS_TOKEN_ERROR);
        }
    }
}