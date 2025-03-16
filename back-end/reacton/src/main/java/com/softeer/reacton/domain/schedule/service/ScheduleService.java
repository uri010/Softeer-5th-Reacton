package com.softeer.reacton.domain.schedule.service;

import com.softeer.reacton.domain.course.entity.Course;
import com.softeer.reacton.domain.course.dto.CourseRequest;
import com.softeer.reacton.domain.course.dto.CourseScheduleResponse;
import com.softeer.reacton.domain.schedule.entity.Schedule;
import com.softeer.reacton.domain.schedule.repository.ScheduleRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {
    private final ScheduleRepository scheduleRepository;

    @Transactional
    public void deleteAllByCourseId(Long courseId) {
        log.info("[Delete Schedules Start] courseId = {}", courseId);

        int deletedCount = scheduleRepository.deleteAllByCourseId(courseId);

        log.info("[Delete Schedules Completed] courseId = {}, deletedCount = {}", courseId, deletedCount);
    }

    public List<Schedule> createSchedules(CourseRequest request, Course course) {
        log.info("[Create Schedules Start] courseId = {}", course.getId());

        List<Schedule> schedules = new ArrayList<>();
        for (CourseRequest.ScheduleRequest scheduleRequest : request.getSchedules()) {
            Schedule schedule = Schedule.create(scheduleRequest, course);
            schedules.add(schedule);
        }

        log.info("[Create Schedules Completed] courseId = {}, totalSchedules = {}", course.getId(), schedules.size());
        return schedules;
    }

    public List<CourseScheduleResponse> getSchedulesByCourseInOrder(Course course) {
        log.info("[Get Schedules Start] courseId = {}", course.getId());

        List<Schedule> schedules = scheduleRepository.findSchedulesByCourse(course);
        log.info("[Get Schedules Completed] courseId = {}, scheduleCount = {}", course.getId(), schedules.size());

        return schedules.stream()
                .map(schedule -> new CourseScheduleResponse(
                        schedule.getDay(),
                        schedule.getStartTime().toString(),
                        schedule.getEndTime().toString()))
                .collect(Collectors.toList());
    }

    public void saveAll(List<Schedule> newSchedules){
        log.info("[Save Schedules Start] totalSchedules = {}", newSchedules.size());

        scheduleRepository.saveAll(newSchedules);
        log.info("[Save Schedules Completed] totalSchedules = {}", newSchedules.size());

    }
}
