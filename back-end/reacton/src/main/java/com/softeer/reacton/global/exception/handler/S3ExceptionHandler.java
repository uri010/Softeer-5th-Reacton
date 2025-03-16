package com.softeer.reacton.global.exception.handler;

import com.softeer.reacton.global.dto.ExceptionResponse;
import com.softeer.reacton.global.exception.code.S3ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@RestControllerAdvice
public class S3ExceptionHandler {

    @ExceptionHandler(NoSuchBucketException.class)
    public ResponseEntity<ExceptionResponse> handleNoSuchBucketException(NoSuchBucketException e) {
        log.error("[S3 Bucket Not Found] Message: {}, StatusCode: {}", e.getMessage(), e.statusCode(), e);
        return ResponseEntity
                .status(S3ErrorCode.S3_BUCKET_NOT_FOUND.getStatus())
                .body(ExceptionResponse.of(S3ErrorCode.S3_BUCKET_NOT_FOUND));
    }

    @ExceptionHandler(NoSuchKeyException.class)
    public ResponseEntity<ExceptionResponse> handleNoSuchKeyException(NoSuchKeyException e) {
        log.error("[S3 Object Not Found] Message: {}, StatusCode: {}", e.getMessage(), e.statusCode(), e);
        return ResponseEntity
                .status(S3ErrorCode.S3_OBJECT_NOT_FOUND.getStatus())
                .body(ExceptionResponse.of(S3ErrorCode.S3_OBJECT_NOT_FOUND));
    }

    @ExceptionHandler(S3Exception.class)
    public ResponseEntity<ExceptionResponse> handleS3Exception(S3Exception e) {
        int statusCode = e.statusCode();
        log.error("[S3 Exception] StatusCode: {}, Message: {}", statusCode, e.getMessage(), e);

        return switch (statusCode) {
            case 400 -> ResponseEntity
                    .status(S3ErrorCode.S3_BAD_REQUEST.getStatus())
                    .body(ExceptionResponse.of(S3ErrorCode.S3_BAD_REQUEST));
            case 403 -> ResponseEntity
                    .status(S3ErrorCode.S3_ACCESS_DENIED.getStatus())
                    .body(ExceptionResponse.of(S3ErrorCode.S3_ACCESS_DENIED));
            default -> ResponseEntity
                    .status(S3ErrorCode.S3_INTERNAL_ERROR.getStatus())
                    .body(ExceptionResponse.of(S3ErrorCode.S3_INTERNAL_ERROR));
        };
    }
}
