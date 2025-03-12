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
        log.debug("수업을 생성합니다.");

        Course course = Course.create(request, professor);

        List<Schedule> schedules = scheduleService.createSchedules(request, course);
        course.setSchedules(schedules);

        List<Request> requests = requestService.createRequests(course);
        course.setRequests(requests);

        return generateAccessCodeAndSave(course);
    }

    @Transactional
    public void updateCourse(Long professorId, long courseId, CourseRequest request) {
        log.debug("수업 데이터를 업데이트합니다. : courseId = {}", courseId);

        if (request == null) {
            log.warn("수업 수정 요청 데이터가 null입니다. : 'request' is null.");
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

        log.info("수업 업데이트가 완료되었습니다. : courseId = {}", courseId);
    }

    @Transactional
    public void deleteCourse(Long professorId, long courseId) {
        log.debug("수업을 삭제합니다. : courseId = {}", courseId);

        if (!courseRepository.existsByIdAndProfessorId(courseId, professorId)) {
            throw new BaseException(CourseErrorCode.COURSE_NOT_FOUND);
        }

        scheduleService.deleteAllByCourseId(courseId);
        questionService.deleteAllByCourseId(courseId);
        requestService.deleteAllByCourseId(courseId);

        courseRepository.deleteByCourseId(courseId);

        log.info("수업이 삭제되었습니다. : courseId = {}", courseId);
    }

    @Transactional
    public void startCourse(Long professorId, long courseId) {
        log.debug("수업을 시작 상태로 변경합니다. courseId = {}", courseId);

        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        boolean wasActive = course.isActive();
        log.debug("시작을 요청한 수업의 활성화 상태입니다. : isActive = {}", wasActive);

        courseRepository.deactivateOtherCourses(professorId, courseId);

        if (!wasActive) {
            questionService.deleteAllByCourseId(course.getId());
            requestService.resetCountByCourseId(course.getId());

            course.activate();
            courseRepository.save(course);
        }
        log.info("수업이 시작 상태로 변경되었습니다. courseId = {}", courseId);
    }

    @Transactional
    public void closeCourse(Long professorId, long courseId) {
        log.debug("수업을 종료 상태로 변경합니다. courseId = {}", courseId);

        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        course.deactivate();
        questionService.deleteCompleteByCourse(course);

        log.debug("SSE 서버에 수업 종료 메시지 전송을 요청합니다.");
        SseMessage<Void> sseMessage = new SseMessage<>("COURSE_CLOSED", null);
        sseMessageSender.sendMessageToAll(String.valueOf(courseId), sseMessage);

        log.info("수업이 종료 상태로 변경되었습니다. courseId = {}", courseId);
    }

    public Map<String, String> uploadFile(Long professorId, long courseId, MultipartFile file) {
        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        courseFileService.deleteFileIfExists(course);

        String fileName = null;
        String s3Key = courseFileService.uploadFile(file);
        if (s3Key != null) {
            fileName = file.getOriginalFilename();
        }
        try {
            professorCourseTransactionService.updateCourseFile(course, fileName, s3Key);
        } catch (Exception e) {
            courseFileService.deleteFileByS3key(s3Key);
            throw new BaseException(FileErrorCode.FILE_UPLOAD_FAILED_DB_ROLLBACK);
        }
        return Map.of("fileName", fileName != null ? fileName : "");
    }

    public Map<String, String> getCourseFileUrl(Long professorId, long courseId) {
        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        String fileUrl = courseFileService.generatePresignedUrl(course);
        return Map.of("fileUrl", fileUrl);
    }

    public void deleteByProfessor(Professor professor) {
        courseRepository.deleteByProfessor(professor);
    }

    private long generateAccessCodeAndSave(Course course) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            int accessCode = generateUniqueAccessCode();
            log.debug("입장 코드 생성 시도 {}회 - {}", i + 1, accessCode);

            course.setAccessCode(accessCode);

            try {
                return professorCourseTransactionService.saveCourse(course);
            } catch (DataIntegrityViolationException e) {
                log.warn("입장 코드 중복으로 인해 저장 실패 - 재시도 {}회: {}", i + 1, accessCode);
            }
        }

        log.error("최대 시도 횟수({}) 초과로 인해 입장 코드 생성 실패", MAX_RETRIES);
        throw new BaseException(CourseErrorCode.ACCESS_CODE_GENERATION_FAILED);
    }

    private int generateUniqueAccessCode() {
        return 100000 + secureRandom.nextInt(900000); // 100000~999999
    }
}