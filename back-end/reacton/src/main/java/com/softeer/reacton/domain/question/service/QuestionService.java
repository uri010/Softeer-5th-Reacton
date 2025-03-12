package com.softeer.reacton.domain.question.service;

import com.softeer.reacton.domain.course.entity.Course;
import com.softeer.reacton.domain.course.service.StudentCourseService;
import com.softeer.reacton.domain.course.dto.CourseQuestionResponse;
import com.softeer.reacton.domain.question.entity.Question;
import com.softeer.reacton.domain.question.repository.QuestionRepository;
import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.CourseErrorCode;
import com.softeer.reacton.global.exception.code.QuestionErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final StudentCourseService studentCourseService;
    private final QuestionRepository questionRepository;

    @Transactional
    public Question checkQuestion(Long questionId) {
        Question question = getQuestion(questionId);
        Course course = question.getCourse();
        checkIfOpen(course);

        question.setIsComplete(true);
        log.debug("질문을 체크합니다. : questionId = {}", questionId);
        return questionRepository.save(question);
    }

    @Transactional
    public Question saveQuestion(String studentId, String content, Long courseId) {
        Course course = studentCourseService.getCourseById(courseId);
        checkIfOpen(course);

        Question question = Question.builder()
                .studentId(studentId)
                .content(content)
                .course(course)
                .build();

        log.debug("질문을 저장합니다. : questionId = {}", question.getId());
        return questionRepository.save(question);
    }

    @Transactional
    public void deleteAllByCourseId(Long courseId) {
        questionRepository.deleteAllByCourseId(courseId);
    }

    public void deleteCompleteByCourse(Course course) {
        questionRepository.deleteCompleteByCourse(course);
    }

    public List<CourseQuestionResponse> getQuestionsByCourseInOrder(Course course) {
        List<Question> questions = questionRepository.findNotCompleteByCourse(course);

        return questions.stream()
                .map(question -> new CourseQuestionResponse(
                        question.getId(),
                        question.getCreatedAt(),
                        question.getContent()
                ))
                .collect(Collectors.toList());
    }

    private Question getQuestion(Long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new BaseException(QuestionErrorCode.QUESTION_NOT_FOUND));
    }

    private void checkIfOpen(Course course) {
        if (!course.isActive()) {
            throw new BaseException(CourseErrorCode.COURSE_NOT_ACTIVE);
        }
    }
}
