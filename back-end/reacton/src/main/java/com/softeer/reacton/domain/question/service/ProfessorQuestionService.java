package com.softeer.reacton.domain.question.service;

import com.softeer.reacton.domain.question.entity.Question;
import com.softeer.reacton.domain.question.dto.QuestionCheckSseRequest;
import com.softeer.reacton.global.sse.SseMessageSender;
import com.softeer.reacton.global.sse.dto.SseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfessorQuestionService {

    private final SseMessageSender sseMessageSender;
    private final QuestionService questionService;

    public void sendQuestionCheck(Long questionId) {
        log.info("[Send Question Check Start] questionId = {}", questionId);

        Question question = questionService.checkQuestion(questionId);
        String studentId = question.getStudentId();

        QuestionCheckSseRequest questionCheckSseRequest = new QuestionCheckSseRequest(questionId);

        log.info("[SSE Message Sending] questionId = {}, studentId = {}", questionId, studentId);
        SseMessage<QuestionCheckSseRequest> sseMessage = new SseMessage<>("QUESTION_CHECK", questionCheckSseRequest);
        sseMessageSender.sendMessage(studentId, sseMessage);

        log.info("[Send Question Check Completed] questionId = {}, studentId = {}", questionId, studentId);
    }
}
