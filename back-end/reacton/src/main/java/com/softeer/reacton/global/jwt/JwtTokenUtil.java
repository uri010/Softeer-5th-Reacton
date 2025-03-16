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

    public String createAuthAccessToken(Long professorId) {
        log.info("[JWT Created] Auth Access Token: professorId = {}", professorId);

        return Jwts.builder()
                .claim("professorId", professorId)
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

    Claims getClaims(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            log.info("[JWT Parsing Success] Claims = {}", claims);  // ✅ 로그 추가하여 claims 값 확인
            return claims;
        } catch (JwtException e) {
            throw new BaseException(JwtErrorCode.ACCESS_TOKEN_ERROR);
        }
    }
}