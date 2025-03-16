package com.softeer.reacton.domain.course.service;

import com.softeer.reacton.domain.course.dto.*;
import com.softeer.reacton.domain.course.entity.Course;
import com.softeer.reacton.domain.course.repository.CourseRepository;
import com.softeer.reacton.domain.professor.entity.Professor;
import com.softeer.reacton.domain.question.service.QuestionService;
import com.softeer.reacton.domain.request.service.RequestService;
import com.softeer.reacton.domain.schedule.entity.Schedule;
import com.softeer.reacton.domain.schedule.service.ScheduleService;
import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.CourseErrorCode;
import com.softeer.reacton.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfessorCourseQueryService {
    private final CourseRepository courseRepository;

    private final ScheduleService scheduleService;
    private final QuestionService questionService;
    private final RequestService requestService;

    public List<Course> getCoursesByProfessor(Professor professor) {
        log.info("[Get Courses Start] professorId = {}", professor.getId());

        List<Course> courses = courseRepository.findByProfessor(professor);
        log.info("[Get Courses Completed] professorId = {}, totalCourses = {}", professor.getId(), courses.size());

        return courses;
    }

    public ActiveCourseResponse getActiveCourseByUser(Long professorId) {
        log.info("[Get Active Course Start] professorId = {}", professorId);

        Pageable pageable = PageRequest.of(0, 1);
        List<Course> course = courseRepository.findLatestActiveCourseByProfessorId(professorId, pageable);

        if (!course.isEmpty()) {
            List<CourseScheduleResponse> schedules = scheduleService.getSchedulesByCourseInOrder(course.get(0));
            log.info("[Get Active Course Completed] professorId = {}, courseId = {}", professorId, course.get(0).getId());
            return ActiveCourseResponse.of(course.get(0), schedules);
        }

        log.info("[Get Active Course Completed] professorId = {}, no active courses found.", professorId);
        return null;
    }

    public CourseDetailResponse getCourseDetail(long courseId, Long professorId) {
        log.info("[Get Course Detail Start] professorId = {}, courseId = {}", professorId, courseId);

        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        List<CourseScheduleResponse> schedules = scheduleService.getSchedulesByCourseInOrder(course);
        List<CourseQuestionResponse> questions = questionService.getQuestionsByCourseInOrder(course);
        List<CourseRequestResponse> requests = requestService.getRequestsByCourseInOrder(course);

        log.info("[Get Course Detail Completed] courseId = {}, schedules = {}, questions = {}, requests = {}",
                courseId, schedules.size(), questions.size(), requests.size());
        return CourseDetailResponse.of(course, schedules, questions, requests);
    }

    public CourseAllResponse getAllCourses(Long professorId) {
        log.info("[Get All Courses Start] professorId = {}", professorId);

        List<Course> allCourses = courseRepository.findCoursesWithSchedulesByProfessorId(professorId);
        List<CourseSummaryResponse> todayCoursesResponse = getTodayCoursesResponse(allCourses);
        List<CourseSummaryResponse> allCoursesResponse = getAllCoursesResponse(allCourses);

        log.info("[Get All Courses Completed] professorId = {}, totalCourses = {}", professorId, allCourses.size());
        return CourseAllResponse.of(todayCoursesResponse, allCoursesResponse);
    }

    public List<CourseSummaryResponse> searchCourses(Long professorId, String keyword) {
        log.info("[Search Courses Start] professorId = {}, keyword = '{}'", professorId, keyword);

        List<Course> searchCourses;
        if (keyword == null || keyword.isEmpty()) {
            searchCourses = courseRepository.findCoursesWithSchedulesByProfessorId(professorId);
        } else {
            String escapedKeyword = escapeWildcard(keyword);
            String searchKeyword = "%" + escapedKeyword + "%";
            searchCourses = courseRepository.findCoursesWithSchedulesByProfessorAndKeyword(professorId, searchKeyword);
        }

        log.info("[Search Courses Completed] professorId = {}, keyword = '{}', foundCourses = {}", professorId, keyword, searchCourses.size());
        return getAllCoursesResponse(searchCourses);
    }

    private List<CourseSummaryResponse> getTodayCoursesResponse(List<Course> allCourses) {
        String currentDay = TimeUtil.getCurrentDay();
        log.debug("[Filter Today Courses] currentDay = {}", currentDay);

        return allCourses.stream()
                .filter(course -> hasScheduleInDay(course, currentDay))
                .sorted(Comparator
                        .comparing(course -> getEarliestStartTime(course, currentDay)))
                .map(course -> CourseSummaryResponse.of(course, getSchedulesByCourse(course)))
                .collect(Collectors.toList());
    }

    private List<CourseScheduleResponse> getSchedulesByCourse(Course course) {
        return course.getSchedules().stream()
                .map(schedule -> new CourseScheduleResponse(
                        schedule.getDay(),
                        schedule.getStartTime().toString(),
                        schedule.getEndTime().toString()))
                .collect(Collectors.toList());
    }

    private boolean hasScheduleInDay(Course course, String day) {
        return course.getSchedules().stream()
                .anyMatch(schedule -> schedule.getDay().equals(day));
    }

    private LocalTime getEarliestStartTime(Course course, String day) {
        return course.getSchedules().stream()
                .filter(schedule -> schedule.getDay().equals(day))
                .map(Schedule::getStartTime)
                .min(Comparator.naturalOrder())
                .orElse(LocalTime.MAX);
    }

    private List<CourseSummaryResponse> getAllCoursesResponse(List<Course> allCourses) {
        return allCourses.stream()
                .map(course -> CourseSummaryResponse.of(course, getSchedulesByCourse(course)))
                .collect(Collectors.toList());
    }

    private String escapeWildcard(String keyword) {
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}