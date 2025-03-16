package com.softeer.reacton.domain.question.service;

import com.softeer.reacton.domain.course.entity.Course;
import com.softeer.reacton.domain.course.repository.CourseRepository;
import com.softeer.reacton.domain.course.dto.CourseQuestionResponse;
import com.softeer.reacton.domain.question.entity.Question;
import com.softeer.reacton.domain.question.repository.QuestionRepository;
import com.softeer.reacton.domain.question.dto.QuestionAllResponse;
import com.softeer.reacton.domain.question.dto.QuestionCheckSseRequest;
import com.softeer.reacton.domain.question.dto.QuestionSendRequest;
import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.CourseErrorCode;
import com.softeer.reacton.global.sse.SseMessageSender;
import com.softeer.reacton.global.sse.dto.SseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentQuestionService {

    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final SseMessageSender sseMessageSender;
    private final QuestionService questionService;

    @Transactional(readOnly = true)
    public QuestionAllResponse getQuestionsByStudentId(String studentId, Long courseId) {
        log.info("[Get Student Questions Start] studentId = {}, courseId = {}", studentId, courseId);

        Course course = getCourse(courseId);
        List<CourseQuestionResponse> questions = findQuestionsNotComplete(studentId, course);

        log.info("[Get Student Questions Completed] studentId = {}, courseId = {}, questionCount = {}",
                studentId, courseId, questions.size());
        return QuestionAllResponse.of(questions);
    }

    public CourseQuestionResponse sendQuestion(String studentId, Long courseId, QuestionSendRequest questionSendRequest) {
        String content = questionSendRequest.getContent();
        log.info("[Send Question Start] studentId = {}, courseId = {}, content = {}", studentId, courseId, content);

        Question question = questionService.saveQuestion(studentId, content, courseId);

        CourseQuestionResponse courseQuestionResponse = new CourseQuestionResponse(
                question.getId(),
                question.getCreatedAt(),
                question.getContent()
        );

        log.info("[SSE Message Sending] studentId = {}, courseId = {}, questionId = {}",
                studentId, courseId, question.getId());
        SseMessage<CourseQuestionResponse> sseMessage = new SseMessage<>("QUESTION", courseQuestionResponse);
        sseMessageSender.sendMessage(courseId.toString(), sseMessage);

        log.info("[Send Question Completed] studentId = {}, courseId = {}, questionId = {}",
                studentId, courseId, question.getId());
        return courseQuestionResponse;
    }

    public void sendQuestionCheck(Long courseId, Long questionId) {
        log.info("[Send Question Check Start] courseId = {}, questionId = {}", courseId, questionId);

        questionService.checkQuestion(questionId);

        QuestionCheckSseRequest questionCheckSseRequest = new QuestionCheckSseRequest(questionId);

        log.info("[SSE Question Check Sending] courseId = {}, questionId = {}", courseId, questionId);

        SseMessage<QuestionCheckSseRequest> sseMessage = new SseMessage<>("QUESTION_CHECK", questionCheckSseRequest);
        sseMessageSender.sendMessage(courseId.toString(), sseMessage);

        log.info("[Send Question Check Completed] courseId = {}, questionId = {}", courseId, questionId);
    }

    private Course getCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));
    }

    private List<CourseQuestionResponse> findQuestionsNotComplete(String studentId, Course course) {
        List<Question> questions =  questionRepository.findNotCompleteByStudentIdAndCourse(studentId, course);

        return questions.stream()
                .map(question -> new CourseQuestionResponse(
                        question.getId(),
                        question.getCreatedAt(),
                        question.getContent()
                ))
                .collect(Collectors.toList());
    }
}
