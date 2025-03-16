package com.softeer.reacton.domain.question.controller;

import com.softeer.reacton.domain.course.dto.CourseQuestionResponse;
import com.softeer.reacton.domain.question.service.StudentQuestionService;
import com.softeer.reacton.domain.question.dto.QuestionAllResponse;
import com.softeer.reacton.domain.question.dto.QuestionSendRequest;
import com.softeer.reacton.global.dto.SuccessResponse;
import com.softeer.reacton.global.jwt.dto.StudentAuthInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/students/questions")
@Tag(name = "Student Questions API", description = "학생 질문 관련 API")
@RequiredArgsConstructor
public class StudentQuestionController {

    private final StudentQuestionService studentQuestionService;

    @GetMapping
    @Operation(
            summary = "학생 질문 목록 조회",
            description = "학생이 이전에 질문했던 목록을 조회합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "성공적으로 조회했습니다."),
                    @ApiResponse(responseCode = "404", description = "수업을 찾을 수 없습니다."),
                    @ApiResponse(responseCode = "409", description = "아직 수업이 시작되지 않았습니다."),
                    @ApiResponse(responseCode = "500", description = "서버와의 연결에 실패했습니다.")
            }
    )
    public ResponseEntity<SuccessResponse<QuestionAllResponse>> getQuestions(StudentAuthInfo studentAuthInfo) {
        QuestionAllResponse response = studentQuestionService.getQuestionsByStudentId(studentAuthInfo.studentId(), studentAuthInfo.courseId());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.of("성공적으로 조회했습니다.", response));
    }

    @PostMapping
    @Operation(
            summary = "학생 질문 전송",
            description = "학생이 교수에게 질문을 전송합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "성공적으로 전송했습니다."),
                    @ApiResponse(responseCode = "404", description = "수업을 찾을 수 없습니다."),
                    @ApiResponse(responseCode = "409", description = "아직 수업이 시작되지 않았습니다."),
                    @ApiResponse(responseCode = "500", description = "서버와의 연결에 실패했습니다.")
            }
    )
    public ResponseEntity<SuccessResponse<CourseQuestionResponse>> sendQuestion(
            StudentAuthInfo studentAuthInfo,
            @Valid @RequestBody QuestionSendRequest questionSendRequest) {
        CourseQuestionResponse response = studentQuestionService.sendQuestion(studentAuthInfo.studentId(), studentAuthInfo.courseId(), questionSendRequest);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.of("질문을 성공적으로 등록했습니다.", response));
    }

    @PostMapping("/check/{questionId}")
    @Operation(
            summary = "학생 질문 체크 전송",
            description = "학생이 교수에게 질문 체크를 전송합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "성공적으로 전송했습니다."),
                    @ApiResponse(responseCode = "404", description = "수업을 찾을 수 없습니다."),
                    @ApiResponse(responseCode = "404", description = "질문을 찾을 수 없습니다."),
                    @ApiResponse(responseCode = "409", description = "아직 수업이 시작되지 않았습니다."),
                    @ApiResponse(responseCode = "500", description = "서버와의 연결에 실패했습니다.")
            }
    )
    public ResponseEntity<Void> checkQuestion(
            StudentAuthInfo studentAuthInfo,
            @PathVariable("questionId") Long questionId) {
        studentQuestionService.sendQuestionCheck(studentAuthInfo.courseId(), questionId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
