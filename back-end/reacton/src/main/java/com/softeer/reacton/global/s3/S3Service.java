package com.softeer.reacton.global.s3;

import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.FileErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.bucket.name}")
    private String bucketName;

    public String uploadFile(MultipartFile file, String folder) {
        String fileName = folder + generateFileName(file);
        log.info("[S3 File Upload Start] fileName = {}", fileName);

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("[S3 File Upload Completed] fileName = {}", fileName);
            return fileName;
        } catch (IOException e) {
            throw new BaseException(FileErrorCode.FILE_READ_FAILED);
        }
    }

    public void deleteFile(String key) {
        log.info("[S3 File Deletion Start] fileKey = {}", key);
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.deleteObject(deleteObjectRequest);

        log.info("[S3 File Deletion Completed] fileKey = {}", key);
    }

    public URL generatePresignedUrl(String key, int expirationMinutes) {
        log.info("[Presigned URL Generation Start] fileKey = {}, expirationMinutes = {}", key, expirationMinutes);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(expirationMinutes))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

        log.info("[Presigned URL Generation Completed] fileKey = {}, presignedUrl = {}", key, presignedRequest.url());

        return presignedRequest.url();
    }

    private String generateFileName(MultipartFile file) {
        return UUID.randomUUID() + "." + file.getOriginalFilename();
    }
}