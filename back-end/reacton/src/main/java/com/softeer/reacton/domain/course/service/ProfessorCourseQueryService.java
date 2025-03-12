package com.softeer.reacton.domain.course.service;

import com.softeer.reacton.domain.course.dto.*;
import com.softeer.reacton.domain.course.entity.Course;
import com.softeer.reacton.domain.course.repository.CourseRepository;
import com.softeer.reacton.domain.file.CourseFileService;
import com.softeer.reacton.domain.professor.entity.Professor;
import com.softeer.reacton.domain.question.service.QuestionService;
import com.softeer.reacton.domain.request.service.RequestService;
import com.softeer.reacton.domain.schedule.entity.Schedule;
import com.softeer.reacton.domain.schedule.service.ScheduleService;
import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.CourseErrorCode;
import com.softeer.reacton.global.sse.SseMessageSender;
import com.softeer.reacton.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
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
        return courseRepository.findByProfessor(professor);
    }

    public ActiveCourseResponse getActiveCourseByUser(Long professorId) {
        log.debug("활성화된 수업을 조회합니다.");

        Course course = courseRepository.findTopByProfessorIdAndIsActiveTrue(professorId).orElse(null);

        if (course != null) {
            List<CourseScheduleResponse> schedules = scheduleService.getSchedulesByCourseInOrder(course);
            return ActiveCourseResponse.of(course, schedules);
        }
        return null;
    }

    public CourseDetailResponse getCourseDetail(long courseId, Long professorId) {
        log.debug("수업 상세 정보를 조회합니다.");

        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        List<CourseScheduleResponse> schedules = scheduleService.getSchedulesByCourseInOrder(course);
        List<CourseQuestionResponse> questions = questionService.getQuestionsByCourseInOrder(course);
        List<CourseRequestResponse> requests = requestService.getRequestsByCourseInOrder(course);

        log.debug("수업 상세 정보를 가져오는 데 성공했습니다. : courseId = {}", courseId);
        return CourseDetailResponse.of(course, schedules, questions, requests);
    }

    public CourseAllResponse getAllCourses(Long professorId) {
        log.debug("전체 수업 목록을 조회합니다.");

        List<Course> allCourses = courseRepository.findCoursesWithSchedulesByProfessorId(professorId);
        List<CourseSummaryResponse> todayCoursesResponse = getTodayCoursesResponse(allCourses);
        List<CourseSummaryResponse> allCoursesResponse = getAllCoursesResponse(allCourses);

        return CourseAllResponse.of(todayCoursesResponse, allCoursesResponse);
    }

    public List<CourseSummaryResponse> searchCourses(Long professorId, String keyword) {
        log.debug("검색 결과를 조회합니다.");

        List<Course> searchCourses;
        if (keyword == null || keyword.isEmpty()) {
            searchCourses = courseRepository.findCoursesWithSchedulesByProfessorId(professorId);
        } else {
            String escapedKeyword = escapeWildcard(keyword);
            String searchKeyword = "%" + escapedKeyword + "%";
            searchCourses = courseRepository.findCoursesWithSchedulesByProfessorAndKeyword(professorId, searchKeyword);
        }

        return getAllCoursesResponse(searchCourses);
    }

    private List<CourseSummaryResponse> getTodayCoursesResponse(List<Course> allCourses) {
        String currentDay = TimeUtil.getCurrentDay();

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