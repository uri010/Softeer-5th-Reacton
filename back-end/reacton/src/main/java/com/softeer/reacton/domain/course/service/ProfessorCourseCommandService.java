package com.softeer.reacton.domain.course.service;

import com.softeer.reacton.domain.course.entity.Course;
import com.softeer.reacton.domain.course.repository.CourseRepository;
import com.softeer.reacton.domain.course.dto.*;
import com.softeer.reacton.domain.file.CourseFileService;
import com.softeer.reacton.domain.professor.entity.Professor;
import com.softeer.reacton.domain.question.service.QuestionService;
import com.softeer.reacton.domain.request.entity.Request;
import com.softeer.reacton.domain.request.service.RequestService;
import com.softeer.reacton.domain.schedule.entity.Schedule;
import com.softeer.reacton.domain.schedule.service.ScheduleService;
import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.CourseErrorCode;
import com.softeer.reacton.global.exception.code.FileErrorCode;
import com.softeer.reacton.global.sse.SseMessageSender;
import com.softeer.reacton.global.sse.dto.SseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfessorCourseCommandService {
    private final CourseRepository courseRepository;

    private final ScheduleService scheduleService;
    private final QuestionService questionService;
    private final RequestService requestService;
    private final ProfessorCourseTransactionService professorCourseTransactionService;
    private final CourseFileService courseFileService;

    private final SseMessageSender sseMessageSender;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final int MAX_RETRIES = 10;

    @Transactional
    public long createCourse(Professor professor, CourseRequest request) {
        log.info("[Course Create Start] professorId = {}", professor.getId());
        Course course = Course.create(request, professor);

        List<Schedule> schedules = scheduleService.createSchedules(request, course);
        course.setSchedules(schedules);

        List<Request> requests = requestService.createRequests(course);
        course.setRequests(requests);

        long courseId = generateAccessCodeAndSave(course);

        log.info("[Course Create Completed] courseId = {}", courseId);
        return courseId;
    }

    @Transactional
    public void updateCourse(Long professorId, long courseId, CourseRequest request) {
        log.info("[Course Update Start] courseId = {}", courseId);

        if (request == null) {
            throw new BaseException(CourseErrorCode.COURSE_REQUEST_IS_NULL);
        }

        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        course.update(request);

        List<CourseRequest.ScheduleRequest> scheduleRequests = request.getSchedules();
        scheduleService.deleteAllByCourseId(courseId);
        course.getSchedules().clear(); // 영속성 컨텍스트에서도 제거

        List<Schedule> newSchedules = scheduleRequests.stream()
                .map(scheduleRequest -> Schedule.create(scheduleRequest, course))
                .collect(Collectors.toList());
        scheduleService.saveAll(newSchedules);
        course.setSchedules(newSchedules);

        log.info("[Course Update Completed] courseId = {}", courseId);
    }

    @Transactional
    public void deleteCourse(Long professorId, long courseId) {
        log.info("[Course Delete Start] courseId = {}", courseId);

        if (!courseRepository.existsByIdAndProfessorId(courseId, professorId)) {
            throw new BaseException(CourseErrorCode.COURSE_NOT_FOUND);
        }

        scheduleService.deleteAllByCourseId(courseId);
        questionService.deleteAllByCourseId(courseId);
        requestService.deleteAllByCourseId(courseId);

        courseRepository.deleteByCourseId(courseId);

        log.info("[Course Delete Completed] courseId = {}", courseId);
    }

    @Transactional
    public void startCourse(Long professorId, long courseId) {
        log.info("[Course Start] courseId = {}", courseId);

        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        boolean wasActive = course.isActive();
        log.debug("[Course Activation Check] courseId = {}, isActive = {}", courseId, wasActive);

        courseRepository.deactivateOtherCourses(professorId, courseId);

        if (!wasActive) {
            log.info("[Course Resetting Data] courseId = {}", courseId);
            questionService.deleteAllByCourseId(course.getId());
            requestService.resetCountByCourseId(course.getId());

            course.activate();
            courseRepository.save(course);
        }
        log.info("[Course Start Completed] courseId = {}", courseId);
    }

    @Transactional
    public void closeCourse(Long professorId, long courseId) {
        log.info("[Course Close] courseId = {}", courseId);

        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        course.deactivate();
        questionService.deleteCompleteByCourse(course);

        log.debug("[Course Close SSE Message Sent] Sending SSE message for courseId = {}", courseId);
        SseMessage<Void> sseMessage = new SseMessage<>("COURSE_CLOSED", null);
        sseMessageSender.sendMessageToAll(String.valueOf(courseId), sseMessage);

        log.info("[Course Close Completed] courseId = {}", courseId);
    }

    public Map<String, String> uploadFile(Long professorId, long courseId, MultipartFile file) {
        log.info("[File Upload Start] courseId = {}, fileName = {}", courseId, file.getOriginalFilename());

        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        courseFileService.deleteFileIfExists(course);

        String fileName = null;
        String s3Key = courseFileService.uploadFile(file);
        if (s3Key != null) {
            fileName = file.getOriginalFilename();
            log.info("[File Uploaded to S3] courseId = {}, fileName = {}, s3Key = {}", courseId, fileName, s3Key);
        }
        try {
            professorCourseTransactionService.updateCourseFile(course, fileName, s3Key);
        } catch (Exception e) {
            courseFileService.deleteFileByS3key(s3Key);
            throw new BaseException(FileErrorCode.FILE_UPLOAD_FAILED_DB_ROLLBACK);
        }

        log.info("[File Upload Completed] courseId = {}, fileName = {}", courseId, fileName);
        return Map.of("fileName", fileName != null ? fileName : "");
    }

    public Map<String, String> getCourseFileUrl(Long professorId, long courseId) {
        log.info("[File Access Start] courseId = {}", courseId);
        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        String fileUrl = courseFileService.generatePresignedUrl(course);
        log.info("[File Access Completed] courseId = {}, fileUrl = {}", courseId, fileUrl);

        return Map.of("fileUrl", fileUrl);
    }

    public void deleteByProfessor(Professor professor) {
        courseRepository.deleteByProfessor(professor);
    }

    private long generateAccessCodeAndSave(Course course) {
        log.info("[Access Code Generation Start] courseId = {}", course.getId());

        for (int i = 0; i < MAX_RETRIES; i++) {
            int accessCode = generateUniqueAccessCode();
            log.debug("[Access Code Attempt] Attempt {} - Code: {}", i + 1, accessCode);

            course.setAccessCode(accessCode);

            try {
                return professorCourseTransactionService.saveCourse(course);
            } catch (DataIntegrityViolationException e) {
                log.warn("[Duplicate Access Code] Retry {} - Code: {}", i + 1, accessCode);
            }
        }

        throw new BaseException(CourseErrorCode.ACCESS_CODE_GENERATION_FAILED);
    }

    private int generateUniqueAccessCode() {
        return 100000 + secureRandom.nextInt(900000); // 100000~999999
    }
}