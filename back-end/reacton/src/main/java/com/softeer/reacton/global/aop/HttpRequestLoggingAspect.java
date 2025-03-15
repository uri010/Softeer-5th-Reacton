package com.softeer.reacton.global.aop;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@Slf4j
public class HttpRequestLoggingAspect {

    private final HttpServletRequest request;

    public HttpRequestLoggingAspect(HttpServletRequest request) {
        this.request = request;
    }

    @Before("execution(* com.softeer.reacton.domain..*Controller.*(..))")
    public void logBeforeController(JoinPoint joinPoint) {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        log.info("📌[API Request] {} {} - {}.{}() - Params: {}", request.getMethod(), request.getRequestURI(), className, methodName, Arrays.toString(args));
    }

    @Around("execution(* com.softeer.reacton.domain..*Service.*(..))")
    public Object logServiceExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String fullClassName = joinPoint.getSignature().getDeclaringTypeName();
        String className = fullClassName.substring(fullClassName.lastIndexOf(".") + 1);
        String methodName = joinPoint.getSignature().getName();

        log.info("📌[Method Start] {}.{}",className, methodName);

        long startTime = System.currentTimeMillis();

        Object result = joinPoint.proceed();

        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        log.info("📌[Method Execution Time] {}.{} executed in {} ms",className, methodName, executionTime);
        return result;
    }
}
