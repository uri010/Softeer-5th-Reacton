package com.softeer.reacton.domain.professor.service;

import com.softeer.reacton.domain.course.entity.Course;
import com.softeer.reacton.domain.course.service.ProfessorCourseQueryService;
import com.softeer.reacton.domain.course.service.ProfessorCourseCommandService;
import com.softeer.reacton.domain.file.CourseFileService;
import com.softeer.reacton.domain.file.ProfessorFileService;
import com.softeer.reacton.domain.professor.dto.ProfessorInfoResponse;
import com.softeer.reacton.domain.professor.entity.Professor;
import com.softeer.reacton.domain.professor.repository.ProfessorRepository;
import com.softeer.reacton.domain.question.service.QuestionService;
import com.softeer.reacton.domain.request.service.RequestService;
import com.softeer.reacton.domain.schedule.service.ScheduleService;
import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.FileErrorCode;
import com.softeer.reacton.global.exception.code.ProfessorErrorCode;
import com.softeer.reacton.global.exception.code.S3ErrorCode;
import com.softeer.reacton.global.jwt.JwtTokenUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfessorService {
    private final JwtTokenUtil jwtTokenUtil;
    private final ProfessorRepository professorRepository;
    private final ProfessorCourseCommandService professorCourseCommandService;
    private final ProfessorCourseQueryService professorCourseQueryService;
    private final ScheduleService scheduleService;
    private final QuestionService questionService;
    private final RequestService requestService;
    private final CourseFileService courseFileService;
    private final ProfessorFileService professorFileService;

    public String signUp(String name, MultipartFile file, String oauthId, String email, Boolean isSignedUp) {
        log.info("[SignUp Start] email = {}, name = {}", email, name);

        if (isSignedUp) {
            throw new BaseException(ProfessorErrorCode.ALREADY_REGISTERED_USER);
        }

        if (professorRepository.findByOauthId(oauthId).isPresent()) {
            throw new BaseException(ProfessorErrorCode.ALREADY_REGISTERED_USER);
        }

        String fileName = null;
        String s3Key = professorFileService.uploadProfileImage(file);
        if (s3Key != null) {
            fileName = file.getOriginalFilename();
        }

        Professor professor = Professor.builder()
                .oauthId(oauthId)
                .email(email)
                .name(name)
                .profileImageFileName(fileName)
                .profileImageS3Key(s3Key)
                .build();
        long professorId = professorRepository.save(professor).getId();

        log.info("[SignUp Completed] email = {}, name = {}, fileName = {}", email, name, fileName);
        return jwtTokenUtil.createAuthAccessToken(professorId);
    }

    @Transactional
    public void delete(String oauthId) {
        log.info("[Delete Professor Start] oauthId = {}", oauthId);

        Professor professor = professorRepository.findByOauthId(oauthId)
                .orElseThrow(() -> new BaseException(ProfessorErrorCode.PROFESSOR_NOT_FOUND));

        professorFileService.deleteProfileImageIfExists(professor);

        List<Course> courses = professorCourseQueryService.getCoursesByProfessor(professor);
        for (Course course : courses) {
            if (courseFileService.isFileExists(course)) {
                courseFileService.deleteFileByS3key(course.getFileS3Key());
            }
            scheduleService.deleteAllByCourseId(course.getId());
            questionService.deleteAllByCourseId(course.getId());
            requestService.deleteAllByCourseId(course.getId());
        }

        professorCourseCommandService.deleteByProfessor(professor);
        professorRepository.delete(professor);

        log.info("[Delete Professor Completed] email = {}", professor.getEmail());
    }

    public ProfessorInfoResponse getProfileInfo(String oauthId) {
        log.info("[Get Profile Info] oauthId = {}", oauthId);

        Professor professor = professorRepository.findByOauthId(oauthId)
                .orElseThrow(() -> new BaseException(ProfessorErrorCode.USER_NOT_FOUND));

        String profileImageUrl = professorFileService.generatePresignedUrl(professor);

        return new ProfessorInfoResponse(professor.getName(), professor.getEmail(), String.valueOf(profileImageUrl));
    }

    public Map<String, String> getProfileImage(String oauthId) {
        log.info("[Get Profile Image] oauthId = {}", oauthId);

        Professor professor = professorRepository.findByOauthId(oauthId)
                .orElseThrow(() -> new BaseException(ProfessorErrorCode.USER_NOT_FOUND));

        String imageUrl = professorFileService.generatePresignedUrl(professor);
        return Map.of("imageUrl", imageUrl);
    }

    public Map<String, String> updateName(String oauthId, String newName) {
        log.info("[Update Name Start] oauthId = {}, newName = {}", oauthId, newName);

        int updatedRows = professorRepository.updateName(oauthId, newName);
        if (updatedRows == 0) {
            throw new BaseException(ProfessorErrorCode.USER_NOT_FOUND);
        }

        log.info("[Update Name Completed] oauthId = {}, newName = {}", oauthId, newName);
        return Map.of("name", newName);
    }

    @Transactional
    public Map<String, String> updateImage(String oauthId, MultipartFile file) {
        log.info("[Update Profile Image Start] oauthId = {}", oauthId);

        Professor professor = professorRepository.findByOauthId(oauthId)
                .orElseThrow(() -> new BaseException(ProfessorErrorCode.PROFESSOR_NOT_FOUND));

        professorFileService.deleteProfileImageIfExists(professor);
        String profileImageFileName = file.getOriginalFilename();
        String profileImageS3Key = professorFileService.uploadProfileImage(file);

        try {
            updateUserProfileImage(oauthId, profileImageFileName, profileImageS3Key);
        } catch (Exception e) {
            professorFileService.deleteFileByS3key(profileImageS3Key);
            throw new BaseException(FileErrorCode.FILE_UPLOAD_FAILED_DB_ROLLBACK);
        }

        String imageUrl = professorFileService.generatePresignedUrl(professor);

        log.info("[Update Profile Image Completed] oauthId = {}, imageUrl = {}", oauthId, imageUrl);
        return Map.of("imageUrl", imageUrl);
    }

    public Long getProfessorIdByOauthId(String oauthId) {
        return professorRepository.findProfessorIdByOauthId(oauthId)
                .orElseThrow(() -> new BaseException(ProfessorErrorCode.PROFESSOR_NOT_FOUND));
    }

    public Professor getProfessorByOauthId(String oauthId) {
        return professorRepository.findByOauthId(oauthId)
                .orElseThrow(() -> new BaseException(ProfessorErrorCode.PROFESSOR_NOT_FOUND));
    }

    private void updateUserProfileImage(String oauthId, String profileImageFileName, String profileImageS3Key) {
        try {
            int updatedRows = professorRepository.updateImage(oauthId, profileImageFileName, profileImageS3Key);
            if (updatedRows == 0) {
                throw new BaseException(ProfessorErrorCode.USER_NOT_FOUND);
            }
        } catch (Exception e) {
            if (profileImageS3Key != null && !profileImageS3Key.isEmpty()) {
                professorFileService.deleteFileByS3key(profileImageS3Key);
            }
            throw new BaseException(S3ErrorCode.S3_INTERNAL_ERROR);
        }
    }
}
