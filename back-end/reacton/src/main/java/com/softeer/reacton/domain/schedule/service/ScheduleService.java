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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScheduleService {
    private final ScheduleRepository scheduleRepository;

    @Transactional
    public void deleteAllByCourseId(Long courseId) {
        scheduleRepository.deleteAllByCourseId(courseId);
    }

    public List<Schedule> createSchedules(CourseRequest request, Course course) {
        List<Schedule> schedules = new ArrayList<>();
        for (CourseRequest.ScheduleRequest scheduleRequest : request.getSchedules()) {
            Schedule schedule = Schedule.create(scheduleRequest, course);
            schedules.add(schedule);
        }
        return schedules;
    }

    public List<CourseScheduleResponse> getSchedulesByCourseInOrder(Course course) {
        List<Schedule> schedules = scheduleRepository.findSchedulesByCourse(course);

        return schedules.stream()
                .map(schedule -> new CourseScheduleResponse(
                        schedule.getDay(),
                        schedule.getStartTime().toString(),
                        schedule.getEndTime().toString()))
                .collect(Collectors.toList());
    }

    public void saveAll(List<Schedule> newSchedules){
        scheduleRepository.saveAll(newSchedules);
    }
}
