package com.softeer.reacton.domain.schedule;

import jakarta.transaction.Transactional;
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
}
