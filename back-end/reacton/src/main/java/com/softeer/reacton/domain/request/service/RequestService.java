package com.softeer.reacton.domain.request.service;

import com.softeer.reacton.domain.course.entity.Course;
import com.softeer.reacton.domain.course.service.StudentCourseService;
import com.softeer.reacton.domain.course.dto.CourseRequestResponse;
import com.softeer.reacton.domain.request.entity.Request;
import com.softeer.reacton.domain.request.repository.RequestRepository;
import com.softeer.reacton.domain.request.enums.RequestType;
import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.CourseErrorCode;
import com.softeer.reacton.global.exception.code.RequestErrorCode;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class RequestService {

    private final StudentCourseService studentCourseService;
    private final RequestRepository requestRepository;

    @Transactional
    public void incrementRequestCount(String content, Long courseId) {
        log.info("[Increment Request Count Start] courseId = {}, content = {}", courseId, content);

        Course course = studentCourseService.getCourseById(courseId);
        checkIfOpen(course);

        try {
            int updatedRows = requestRepository.incrementCount(course, content);
            if (updatedRows == 0) {
                throw new BaseException(RequestErrorCode.REQUEST_NOT_FOUND);
            }
        } catch (DataIntegrityViolationException e) {
            throw new BaseException(RequestErrorCode.REQUEST_OVERFLOW);
        }
    }

    @Transactional
    public void deleteAllByCourseId(Long courseId) {
        log.info("[Delete All Requests Start] courseId = {}", courseId);

        int deletedCount = requestRepository.deleteAllByCourseId(courseId);
        log.info("[Delete All Requests Completed] courseId = {}, deletedCount = {}", courseId, deletedCount);
    }

    public List<Request> createRequests(Course course) {
        log.info("[Create Default Requests] courseId = {}", course.getId());

        List<Request> requests = RequestType.getRequestTypes().stream()
                .map(requestType -> Request.create(requestType, course))
                .collect(Collectors.toList());

        log.info("[Default Requests Created] courseId = {}, totalRequests = {}", course.getId(), requests.size());
        return requests;
    }

    public List<CourseRequestResponse> getRequestsByCourseInOrder(Course course) {
        log.info("[Get Requests Start] courseId = {}", course.getId());

        List<Request> requests = course.getRequests();
        log.info("[Get Requests Completed] courseId = {}, requestCount = {}", course.getId(), requests.size());

        return requests.stream()
                .map(request -> new CourseRequestResponse(
                        request.getType(),
                        request.getCount()
                ))
                .collect(Collectors.toList());
    }

    public void resetCountByCourseId(Long courseId) {
        log.info("[Reset Request Count Start] courseId = {}", courseId);

        int resetCount = requestRepository.resetCountByCourseId(courseId);
        log.info("[Reset Request Count Completed] courseId = {}, resetCount = {}", courseId, resetCount);
    }

    private void checkIfOpen(Course course) {
        if (!course.isActive()) {
            throw new BaseException(CourseErrorCode.COURSE_NOT_ACTIVE);
        }
    }
}
