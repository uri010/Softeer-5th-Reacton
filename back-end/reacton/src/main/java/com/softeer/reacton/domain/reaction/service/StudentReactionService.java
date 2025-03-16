package com.softeer.reacton.domain.reaction.service;

import com.softeer.reacton.domain.course.entity.Course;
import com.softeer.reacton.domain.course.repository.CourseRepository;
import com.softeer.reacton.domain.reaction.dto.ReactionSendRequest;
import com.softeer.reacton.domain.reaction.dto.ReactionSseRequest;
import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.CourseErrorCode;
import com.softeer.reacton.global.sse.SseMessageSender;
import com.softeer.reacton.global.sse.dto.SseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentReactionService {

    private final CourseRepository courseRepository;
    private final SseMessageSender sseMessageSender;

    public void sendReaction(Long courseId, ReactionSendRequest reactionSendRequest) {
        String content = reactionSendRequest.getContent();
        log.info("[Send Reaction Start] courseId = {}, content = {}", courseId, content);

        Course course = getCourse(courseId);
        checkIfOpen(course);

        ReactionSseRequest reactionSseRequest = new ReactionSseRequest(content);

        log.info("[SSE Message Sending] courseId = {}, content = {}", courseId, content);

        SseMessage<ReactionSseRequest> sseMessage = new SseMessage<>("REACTION", reactionSseRequest);
        sseMessageSender.sendMessage(courseId.toString(), sseMessage);

        log.info("[Send Reaction Completed] courseId = {}, content = {}", courseId, content);
    }

    private Course getCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));
    }

    private void checkIfOpen(Course course) {
        if (!course.isActive()) {
            throw new BaseException(CourseErrorCode.COURSE_NOT_ACTIVE);
        }
    }
}
