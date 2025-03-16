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
        log.info("[Check Question Start] questionId = {}", questionId);

        Question question = getQuestion(questionId);
        Course course = question.getCourse();
        checkIfOpen(course);

        question.setIsComplete(true);
        log.info("[Check Question Completed] questionId = {}, courseId = {}", questionId, course.getId());
        return questionRepository.save(question);
    }

    @Transactional
    public Question saveQuestion(String studentId, String content, Long courseId) {
        log.info("[Save Question Start] studentId = {}, courseId = {}", studentId, courseId);

        Course course = studentCourseService.getCourseById(courseId);
        checkIfOpen(course);

        Question question = Question.builder()
                .studentId(studentId)
                .content(content)
                .course(course)
                .build();

        Question savedQuestion = questionRepository.save(question);
        log.info("[Save Question Completed] questionId = {}, studentId = {}, courseId = {}", savedQuestion.getId(), studentId, courseId);
        return savedQuestion;
    }

    @Transactional
    public void deleteAllByCourseId(Long courseId) {
        log.info("[Delete All Questions Start] courseId = {}", courseId);

        int deletedCount = questionRepository.deleteAllByCourseId(courseId);

        log.info("[Delete All Questions Completed] courseId = {}, deletedCount = {}", courseId, deletedCount);
    }

    public void deleteCompleteByCourse(Course course) {
        log.info("[Delete Completed Questions Start] courseId = {}", course.getId());

        int deletedCount = questionRepository.deleteCompleteByCourse(course);

        log.info("[Delete Completed Questions Completed] courseId = {}, deletedCount = {}", course.getId(), deletedCount);
    }

    public List<CourseQuestionResponse> getQuestionsByCourseInOrder(Course course) {
        log.info("[Get Questions Start] courseId = {}", course.getId());

        List<Question> questions = questionRepository.findNotCompleteByCourse(course);
        log.info("[Get Questions Completed] courseId = {}, questionCount = {}", course.getId(), questions.size());

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
