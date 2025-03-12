package com.softeer.reacton.domain.schedule;

import com.softeer.reacton.domain.course.Course;
import com.softeer.reacton.domain.course.dto.CourseRequest;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
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
}
