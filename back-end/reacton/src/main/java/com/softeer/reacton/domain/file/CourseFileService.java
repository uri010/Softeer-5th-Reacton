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
        if (!validateFile(file)) {
            return null;
        }
        return s3Service.uploadFile(file, FILE_DIRECTORY);
    }

    public void deleteFileIfExists(Course course) {
        if (isFileExists(course)) {
            s3Service.deleteFile(course.getFileS3Key());
            log.debug("기존 강의자료 파일 삭제 완료: fileName = {}", course.getFileName());
        }
    }

    public String generatePresignedUrl(Course course) {
        return isFileExists(course)
                ? s3Service.generatePresignedUrl(course.getFileS3Key(), PRESIGNED_URL_EXPIRATION_MINUTES).toString()
                : "";
    }

    public void deleteFileByS3key(String profileImageS3Key) {
        s3Service.deleteFile(profileImageS3Key);
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
