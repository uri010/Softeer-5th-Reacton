package com.softeer.reacton.domain.file;

import com.softeer.reacton.domain.course.entity.Course;
import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.FileErrorCode;
import com.softeer.reacton.global.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseFileService {
    private final S3Service s3Service;

    private static final String FILE_DIRECTORY = "course-files/";
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;
    private static final int PRESIGNED_URL_EXPIRATION_MINUTES = 1;

    public String uploadFile(MultipartFile file) {
        log.info("[File Upload Start] fileName = {}", file.getOriginalFilename());

        if (!validateFile(file)) {
            return null;
        }

        String s3Key = s3Service.uploadFile(file, FILE_DIRECTORY);
        log.info("[File Upload Completed] fileName = {}, s3Key = {}", file.getOriginalFilename(), s3Key);
        return s3Key;
    }

    public void deleteFileIfExists(Course course) {
        if (isFileExists(course)) {
            log.info("[File Deletion Start] fileName = {}, s3Key = {}", course.getFileName(), course.getFileS3Key());
            s3Service.deleteFile(course.getFileS3Key());
            log.info("[File Deletion Completed] fileName = {}", course.getFileName());
        }
    }

    public String generatePresignedUrl(Course course) {
        log.info("[Generate Presigned URL Start] courseId = {}", course.getId());

        if (!isFileExists(course)) {
            log.warn("[Generate Presigned URL Failed] No file found for courseId = {}", course.getId());
            return "";
        }

        String url = s3Service.generatePresignedUrl(course.getFileS3Key(), PRESIGNED_URL_EXPIRATION_MINUTES).toString();
        log.info("[Generate Presigned URL Completed] courseId = {}, fileUrl = {}", course.getId(), url);
        return url;
    }

    public void deleteFileByS3key(String fileS3Key) {
        log.info("[File Deletion Start] s3Key = {}", fileS3Key);
        s3Service.deleteFile(fileS3Key);
        log.info("[File Deletion Completed] s3Key = {}", fileS3Key);
    }

    public boolean isFileExists(Course course) {
        return course.getFileName() != null && !course.getFileName().isEmpty()
                && course.getFileS3Key() != null && !course.getFileS3Key().isEmpty();
    }

    private boolean validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BaseException(FileErrorCode.FILE_SIZE_EXCEEDED);
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new BaseException(FileErrorCode.INVALID_FILE_TYPE);
        }
        String mimeType = file.getContentType();
        if (mimeType == null || !mimeType.equals("application/pdf")) {
            throw new BaseException(FileErrorCode.INVALID_FILE_TYPE);
        }
        return true;
    }
}
