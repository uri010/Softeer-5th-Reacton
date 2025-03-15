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
        Course course = studentCourseService.getCourseById(courseId);
        checkIfOpen(course);

        log.debug("요청을 저장합니다.");
        try {
            int updatedRows = requestRepository.incrementCount(course, content);
            if (updatedRows == 0) {
                log.debug("요청 데이터를 처리하는 과정에서 발생한 에러입니다. : Request does not exist.");
                throw new BaseException(RequestErrorCode.REQUEST_NOT_FOUND);
            }
        } catch (DataIntegrityViolationException e) {
            log.debug("요청 데이터를 처리하는 과정에서 발생한 에러입니다. : {}", e.getMessage());
            throw new BaseException(RequestErrorCode.REQUEST_OVERFLOW);
        }
    }

    @Transactional
    public void deleteAllByCourseId(Long courseId) {
        requestRepository.deleteAllByCourseId(courseId);
    }

    public List<Request> createRequests(Course course) {
        return RequestType.getRequestTypes().stream()
                .map(requestType -> Request.create(requestType, course))
                .collect(Collectors.toList());
    }

    public List<CourseRequestResponse> getRequestsByCourseInOrder(Course course) {
        List<Request> requests = course.getRequests();

        return requests.stream()
                .map(request -> new CourseRequestResponse(
                        request.getType(),
                        request.getCount()
                ))
                .collect(Collectors.toList());
    }

    public void resetCountByCourseId(Long courseId) {
        requestRepository.resetCountByCourseId(courseId);
    }

    private void checkIfOpen(Course course) {
        if (!course.isActive()) {
            throw new BaseException(CourseErrorCode.COURSE_NOT_ACTIVE);
        }
    }
}
