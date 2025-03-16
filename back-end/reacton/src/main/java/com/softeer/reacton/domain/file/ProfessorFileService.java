package com.softeer.reacton.domain.file;

import com.softeer.reacton.domain.professor.entity.Professor;
import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.FileErrorCode;
import com.softeer.reacton.global.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfessorFileService {

    private final S3Service s3Service;

    private static final Set<String> ALLOWED_IMAGE_FILE_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final long MAX_IMAGE_FILE_SIZE = 5L * 1024 * 1024;
    private static final String PROFILE_DIRECTORY = "profiles/";
    private static final int PRESIGNED_URL_EXPIRATION_MINUTES = 1;

    public void deleteProfileImageIfExists(Professor professor) {
        if (isFileExists(professor)) {
            log.info("[Profile Image Deletion Start] professorId = {}, s3Key = {}", professor.getId(), professor.getProfileImageS3Key());
            s3Service.deleteFile(professor.getProfileImageS3Key());
            log.info("[Profile Image Deletion Completed] professorId = {}", professor.getId());
        }
    }

    public String uploadProfileImage(MultipartFile file) {
        log.info("[Profile Image Upload Start] fileName = {}", file.getOriginalFilename());

        if (!validateProfileImage(file)) {
            return null;
        }
        String s3Key = s3Service.uploadFile(file, PROFILE_DIRECTORY);
        log.info("[Profile Image Upload Completed] fileName = {}, s3Key = {}", file.getOriginalFilename(), s3Key);
        return s3Key;
    }

    public String generatePresignedUrl(Professor professor) {
        log.info("[Generate Presigned URL Start] professorId = {}", professor.getId());

        if (!isFileExists(professor)) {
            log.warn("[Generate Presigned URL Failed] No profile image found for professorId = {}", professor.getId());
            return "";
        }

        String url = s3Service.generatePresignedUrl(professor.getProfileImageS3Key(), PRESIGNED_URL_EXPIRATION_MINUTES).toString();
        log.info("[Generate Presigned URL Completed] professorId = {}, fileUrl = {}", professor.getId(), url);
        return url;
    }

    public void deleteFileByS3key(String profileImageS3Key) {
        log.info("[File Deletion Start] s3Key = {}", profileImageS3Key);
        s3Service.deleteFile(profileImageS3Key);
        log.info("[File Deletion Completed] s3Key = {}", profileImageS3Key);
    }

    private boolean isFileExists(Professor professor) {
        return professor.getProfileImageFileName() != null && !professor.getProfileImageFileName().isEmpty()
                && professor.getProfileImageS3Key() != null && !professor.getProfileImageS3Key().isEmpty();
    }

    private boolean validateProfileImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        if (file.getSize() > MAX_IMAGE_FILE_SIZE) {
            throw new BaseException(FileErrorCode.IMAGE_FILE_SIZE_EXCEEDED);
        }

        String fileName = file.getOriginalFilename();
        if (fileName != null && !fileName.isEmpty()) {
            String fileExtension = getFileExtension(fileName);
            if (!ALLOWED_IMAGE_FILE_EXTENSIONS.contains(fileExtension.toLowerCase())) {
                throw new BaseException(FileErrorCode.INVALID_IMAGE_FILE_TYPE);
            }
        }

        return true;
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf(".");
        return (lastDotIndex == -1) ? "" : fileName.substring(lastDotIndex + 1);
    }
}
