package com.softeer.reacton.global.config;

import com.softeer.reacton.global.jwt.AccessTokenArgumentResolver;
import com.softeer.reacton.global.jwt.SignupTokenArgumentResolver;
import com.softeer.reacton.global.jwt.StudentTokenArgumentResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final SignupTokenArgumentResolver signupTokenArgumentResolver;
    private final AccessTokenArgumentResolver accessTokenArgumentResolver;
    private final StudentTokenArgumentResolver studentTokenArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(signupTokenArgumentResolver);
        resolvers.add(accessTokenArgumentResolver);
        resolvers.add(studentTokenArgumentResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("https://softeer-reacton.shop")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}