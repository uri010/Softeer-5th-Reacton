package com.softeer.reacton.global.oauth;

import com.softeer.reacton.domain.professor.entity.Professor;
import com.softeer.reacton.domain.professor.repository.ProfessorRepository;
import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.GlobalErrorCode;
import com.softeer.reacton.global.exception.code.OAuthErrorCode;
import com.softeer.reacton.global.jwt.JwtTokenUtil;
import com.softeer.reacton.global.oauth.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final OAuthConfig oauthConfig;
    private final JwtTokenUtil jwtTokenUtil;
    private final ProfessorRepository professorRepository;
    private final WebClient webClient;

    public String getOauthLoginUrl(String providerName) {
        log.info("[Generate OAuth Login URL] provider = {}", providerName);

        OAuthProvider provider = oauthConfig.getProvider(providerName);
        StringBuilder urlBuilder = new StringBuilder(provider.getLoginUrl());

        urlBuilder.append("?scope=").append(URLEncoder.encode(provider.getScope(), StandardCharsets.UTF_8))
                .append("&client_id=").append(provider.getClientId())
                .append("&redirect_uri=").append(URLEncoder.encode(provider.getRedirectUri(), StandardCharsets.UTF_8))
                .append("&prompt=").append(provider.getPrompt())
                .append("&response_type=code");

        return urlBuilder.toString();
    }

    public OAuthLoginResult processOauthLogin(String providerName, String code) {
        log.info("[OAuth Login Start] provider = {}, code = {}", providerName, code);

        if (code == null || code.isEmpty()) {
            throw new BaseException(GlobalErrorCode.MISSING_PARAMETER);
        }

        OAuthProvider provider = oauthConfig.getProvider(providerName);

        OAuthTokenResponse tokenResponse = getAuthAccessTokenByOauth(code, provider);
        UserProfile userProfile = getUserProfile(providerName, provider, tokenResponse);

        Optional<Professor> existingUser = professorRepository.findByOauthId(userProfile.getOauthId());

        existingUser.ifPresent(professor -> {
            if (!professor.getEmail().equals(userProfile.getEmail())) {
                log.info("[Update User Email] oauthId = {}, oldEmail = {}, newEmail = {}",
                        professor.getOauthId(), professor.getEmail(), userProfile.getEmail());

                professor.updateEmail(userProfile.getEmail());
                professorRepository.save(professor);

                log.info("[User Email Updated] oauthId = {}", professor.getOauthId());
            }
        });

        boolean isSignedUp = existingUser.isPresent();
        String accessToken = isSignedUp
                ? jwtTokenUtil.createAuthAccessToken(userProfile.getOauthId(), userProfile.getEmail())
                : jwtTokenUtil.createSignUpToken(userProfile.getOauthId(), userProfile.getEmail());

        log.info("[OAuth Login Completed] provider = {}, isSignedUp = {}", providerName, isSignedUp);

        return new OAuthLoginResult(accessToken, isSignedUp);
    }

    private OAuthTokenResponse getAuthAccessTokenByOauth(String code, OAuthProvider provider) {
        log.info("[Request OAuth Access Token] provider = {}, code = {}", provider.getClass(), code);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code", code);
        formData.add("client_id", provider.getClientId());
        formData.add("client_secret", provider.getClientSecret());
        formData.add("redirect_uri", provider.getRedirectUri());
        formData.add("grant_type", "authorization_code");

        try {
            OAuthTokenResponse tokenResponse = webClient.post()
                    .uri(provider.getTokenUri())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .bodyValue(formData)
                    .retrieve()
                    .bodyToMono(OAuthTokenResponse.class)
                    .block();

            log.info("[OAuth Access Token Received] provider = {}", provider.getClass());
            return tokenResponse;
        } catch (WebClientResponseException e) {
            throw new BaseException(GlobalErrorCode.SERVER_ERROR);
        }
    }

    private UserProfile getUserProfile(String providerName, OAuthProvider provider, OAuthTokenResponse tokenResponse) {
        log.info("[Request OAuth User Profile] provider = {}", providerName);

        if ("google".equals(providerName)) {
            UserProfile userProfile = webClient.get()
                    .uri(provider.getUserInfoUri())
                    .headers(header -> header.setBearerAuth(tokenResponse.getAccessToken()))
                    .retrieve()
                    .bodyToMono(GoogleUserProfile.class)
                    .block();

            log.info("[OAuth User Profile Retrieved] provider = {}, oauthId = {}", providerName, userProfile.getOauthId());
            return userProfile;
        }

        throw new BaseException(OAuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
    }
}
