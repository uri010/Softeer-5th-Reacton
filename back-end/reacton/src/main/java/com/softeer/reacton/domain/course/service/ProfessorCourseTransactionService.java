package com.softeer.reacton.domain.course.service;

import com.softeer.reacton.domain.course.entity.Course;
import com.softeer.reacton.domain.course.repository.CourseRepository;
import com.softeer.reacton.domain.request.entity.Request;
import com.softeer.reacton.domain.request.repository.RequestRepository;
import com.softeer.reacton.domain.schedule.entity.Schedule;
import com.softeer.reacton.domain.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfessorCourseTransactionService {
    private final CourseRepository courseRepository;
    private final ScheduleRepository scheduleRepository;
    private final RequestRepository requestRepository;

    @Transactional
    public long saveCourse(Course course) {
        log.info("[Save Course Start] courseId = {}", course.getId());

        courseRepository.save(course);
        log.debug("[Course Saved] courseId = {}", course.getId());

        for (Schedule schedule : course.getSchedules()) {
            schedule.setCourse(course);
            scheduleRepository.save(schedule);
        }
        log.debug("[Schedules Saved] courseId = {}, totalSchedules = {}", course.getId(), course.getSchedules().size());

        for (Request request : course.getRequests()) {
            request.setCourse(course);
            requestRepository.save(request);
        }
        log.debug("[Requests Saved] courseId = {}, totalRequests = {}", course.getId(), course.getRequests().size());

        log.info("[Save Course Completed] courseId = {}", course.getId());
        return course.getId();
    }

    @Transactional
    public void updateCourseFile(Course course, String fileName, String s3Key) {
        log.info("[Update Course File Start] courseId = {}, oldFileName = {}, newFileName = {}",
                course.getId(), course.getFileName(), fileName);

        course.setFileName(fileName);
        course.setFileS3Key(s3Key);
        courseRepository.save(course);

        log.info("[Update Course File Completed] courseId = {}, newFileName = {}, newS3Key = {}",
                course.getId(), course.getFileName(), course.getFileS3Key());
    }
}
