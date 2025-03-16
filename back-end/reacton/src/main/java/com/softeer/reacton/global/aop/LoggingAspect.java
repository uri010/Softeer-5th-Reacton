package com.softeer.reacton.global.aop;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    private final HttpServletRequest request;

    public LoggingAspect(HttpServletRequest request) {
        this.request = request;
    }

    @Before("execution(* com.softeer.reacton.domain..*Controller.*(..))")
    public void logBeforeController(JoinPoint joinPoint) {
        String fullClassName = joinPoint.getSignature().getDeclaringTypeName();
        String className = fullClassName.substring(fullClassName.lastIndexOf(".") + 1);
        String methodName = joinPoint.getSignature().getName();

        log.info("[API Request] {} {} - {}.{}()", request.getMethod(), request.getRequestURI(), className, methodName);
    }

    @Around("execution(* com.softeer.reacton.domain..*Service.*(..))")
    public Object logServiceExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String fullClassName = joinPoint.getSignature().getDeclaringTypeName();
        String className = fullClassName.substring(fullClassName.lastIndexOf(".") + 1);
        String methodName = joinPoint.getSignature().getName();

        long startTime = System.currentTimeMillis();

        Object result = joinPoint.proceed();

        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        log.info("[Method Execution Time] {}.{}() executed in {} ms", className, methodName, executionTime);
        return result;
    }
}
