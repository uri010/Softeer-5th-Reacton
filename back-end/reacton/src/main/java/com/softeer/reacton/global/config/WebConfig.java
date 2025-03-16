package com.softeer.reacton.global.config;

import com.softeer.reacton.global.jwt.ProfessorAuthResolver;
import com.softeer.reacton.global.jwt.ProfessorSignupResolver;
import com.softeer.reacton.global.jwt.StudentAuthResolver;
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

    private final ProfessorSignupResolver professorSignupResolver;
    private final ProfessorAuthResolver professorAuthResolver;
    private final StudentAuthResolver studentAuthResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(professorSignupResolver);
        resolvers.add(professorAuthResolver);
        resolvers.add(studentAuthResolver);
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