package com.softeer.reacton.domain.course.service;

import com.softeer.reacton.domain.course.entity.Course;
import com.softeer.reacton.domain.course.repository.CourseRepository;
import com.softeer.reacton.domain.course.dto.CourseScheduleResponse;
import com.softeer.reacton.domain.course.dto.CourseSummaryResponse;
import com.softeer.reacton.domain.schedule.entity.Schedule;
import com.softeer.reacton.domain.schedule.repository.ScheduleRepository;
import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.CourseErrorCode;
import com.softeer.reacton.global.jwt.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentCourseService {

    private final JwtTokenUtil jwtTokenUtil;
    private final CourseRepository courseRepository;
    private final ScheduleRepository scheduleRepository;

    public CourseSummaryResponse getCourseByAccessCode(int accessCode) {
        log.info("[Get Course By Access Code Start] accessCode = {}", accessCode);

        Optional<Course> existingCourse = courseRepository.findByAccessCode(accessCode);

        Course course = existingCourse.orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        if (!course.isActive()) {
            log.debug("수업 정보를 가져오는 과정에서 발생한 에러입니다. : Course is not active.");
            throw new BaseException(CourseErrorCode.COURSE_NOT_ACTIVE);
        }

        List<CourseScheduleResponse> schedules = getSchedulesByCourseId(course);

        log.info("[Get Course By Access Code Completed] courseId = {}", course.getId());
        return CourseSummaryResponse.of(course, schedules);
    }

    public String registerCourse(int accessCode) {
        log.info("[Register Course Start] accessCode = {}", accessCode);

        Course course = getCourse(accessCode);
        checkIfOpen(course);

        String studentId = UUID.randomUUID().toString();
        log.info("[Generated Temporary Student ID] studentId = {}", studentId);

        String token = jwtTokenUtil.createStudentAccessToken(studentId, course.getId());
        log.info("[Register Course Completed] courseId = {}, studentId = {}", course.getId(), studentId);

        return token;
    }

    public Course getCourseById(Long courseId) {
        log.info("[Get Course By ID] courseId = {}", courseId);

        return courseRepository.findById(courseId).orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));
    }

    private List<CourseScheduleResponse> getSchedulesByCourseId(Course course) {
        List<Schedule> schedules = scheduleRepository.findSchedulesByCourse(course);

        return schedules.stream()
                .map(schedule -> new CourseScheduleResponse(
                        schedule.getDay(),
                        schedule.getStartTime().toString(),
                        schedule.getEndTime().toString()
                ))
                .collect(Collectors.toList());
    }

    private Course getCourse(int accessCode) {
        return courseRepository.findByAccessCode(accessCode)
                .orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));
    }

    private void checkIfOpen(Course course) {
        if (!course.isActive()) {
            throw new BaseException(CourseErrorCode.COURSE_NOT_ACTIVE);
        }
    }
}
