package com.softeer.reacton.domain.course.controller;

import com.softeer.reacton.domain.course.service.ProfessorCourseQueryService;
import com.softeer.reacton.domain.course.service.ProfessorCourseCommandService;
import com.softeer.reacton.domain.course.dto.*;
import com.softeer.reacton.domain.professor.entity.Professor;
import com.softeer.reacton.domain.professor.service.ProfessorService;
import com.softeer.reacton.global.dto.SuccessResponse;
import com.softeer.reacton.global.jwt.dto.LoginProfessor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/professors/courses")
@Tag(name = "Professor Course API", description = "교수 수업 관련 API")
@RequiredArgsConstructor
public class ProfessorCourseController {
    private final ProfessorCourseCommandService professorCourseCommandService;
    private final ProfessorCourseQueryService professorCourseQueryService;
    private final ProfessorService professorService;

    @Value("${frontend.base-url}")
    private String FRONTEND_BASE_URL;

    @GetMapping("/active")
    @Operation(
            summary = "활성화된 수업 조회",
            description = "활성화된 수업을 조회합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "활성화된 수업이 존재합니다."),
                    @ApiResponse(responseCode = "303", description = "활성화된 수업이 없습니다. 홈 화면으로 이동합니다.")
            }
    )
    public ResponseEntity<?> getActiveCourses(LoginProfessor loginProfessor) {
        ActiveCourseResponse activeCourse = professorCourseQueryService.getActiveCourseByUser(loginProfessor.id());

        if (activeCourse != null) {
            return ResponseEntity.status(HttpStatus.OK)
                    .body(SuccessResponse.of("활성화된 수업이 존재합니다.", activeCourse));
        } else {
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .header(HttpHeaders.LOCATION, FRONTEND_BASE_URL + "professor")
                    .build();
        }
    }

    @PostMapping
    @Operation(
            summary = "수업 생성 요청",
            description = "수업 데이터를 받아 데이터베이스 저장하고 courseId를 반환합니다.",
            responses = {@ApiResponse(responseCode = "201", description = "수업이 생성되었습니다.")}
    )
    public ResponseEntity<SuccessResponse<Map<String, String>>> createCourse(LoginProfessor loginProfessor, @RequestBody @Valid @NonNull CourseRequest courseRequest) {
        Professor professor = professorService.getProfessorById(loginProfessor.id());

        long courseId = professorCourseCommandService.createCourse(professor, courseRequest);

        Map<String, String> response = new HashMap<>();
        response.put("courseId", String.valueOf(courseId));

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.of("수업이 생성되었습니다.", response));
    }

    @GetMapping("/{courseId}")
    @Operation(
            summary = "수업 상세 조회 요청",
            description = "courseId에 해당하는 수업의 상세 정보를 조회합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회에 성공했습니다."),
                    @ApiResponse(responseCode = "403", description = "해당 수업에 접근할 권한이 없습니다."),
                    @ApiResponse(responseCode = "404", description = "정보를 찾을 수 없습니다.")
            }
    )
    public ResponseEntity<SuccessResponse<CourseDetailResponse>> getCourseDetail(
            LoginProfessor loginProfessor,
            @PathVariable("courseId") Long courseId
    ) {
        CourseDetailResponse response = professorCourseQueryService.getCourseDetail(courseId, loginProfessor.id());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.of("성공적으로 조회했습니다.", response));
    }

    @GetMapping("/home")
    @Operation(
            summary = "전체 수업 목록 조회",
            description = "사용자의 전체 수업 정보를 조회합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회에 성공했습니다."),
                    @ApiResponse(responseCode = "404", description = "정보를 찾을 수 없습니다.")
            }
    )
    public ResponseEntity<SuccessResponse<CourseAllResponse>> getAllCourses(LoginProfessor loginProfessor) {
        CourseAllResponse response = professorCourseQueryService.getAllCourses(loginProfessor.id());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.of("성공적으로 조회했습니다.", response));
    }

    @GetMapping(params = "keyword")
    @Operation(
            summary = "수업 검색",
            description = "키워드로 수업을 검색합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "검색 결과를 반환합니다."),
                    @ApiResponse(responseCode = "404", description = "검색 결과가 없습니다.")
            }
    )
    public ResponseEntity<SuccessResponse<List<CourseSummaryResponse>>> searchCourses(
            LoginProfessor loginProfessor,
            @RequestParam("keyword") String keyword) {
        List<CourseSummaryResponse> response = professorCourseQueryService.searchCourses(loginProfessor.id(), keyword);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.of("성공적으로 조회했습니다.", response));
    }

    @PutMapping("/{courseId}")
    @Operation(
            summary = "수업 수정 요청",
            description = "수업 데이터를 기존 데이터에 업데이트하고 courseId를 반환합니다.",
            responses = {@ApiResponse(responseCode = "200", description = "수업이 수정되었습니다.")}
    )
    public ResponseEntity<SuccessResponse<Map<String, String>>> updateCourse(
            LoginProfessor loginProfessor,
            @PathVariable(value = "courseId") long courseId,
            @RequestBody @Valid CourseRequest courseRequest) {
        professorCourseCommandService.updateCourse(loginProfessor.id(), courseId, courseRequest);

        Map<String, String> response = new HashMap<>();
        response.put("courseId", String.valueOf(courseId));

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.of("수업이 수정되었습니다.", response));
    }

    @DeleteMapping("/{courseId}")
    @Operation(
            summary = "수업 삭제 요청",
            description = "courseId에 해당하는 수업을 삭제합니다.",
            responses = {@ApiResponse(responseCode = "204", description = "수업이 삭제되었습니다.")}
    )
    public ResponseEntity<Void> deleteCourse(LoginProfessor loginProfessor, @PathVariable(value = "courseId") long courseId) {
        professorCourseCommandService.deleteCourse(loginProfessor.id(), courseId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{courseId}/start")
    @Operation(
            summary = "수업 시작 상태로 변경",
            description = "courseId에 해당하는 수업을 시작 상태로 변경합니다.",
            responses = {@ApiResponse(responseCode = "204", description = "수업이 시작 상태로 변경되었습니다.")}
    )
    public ResponseEntity<Void> startCourse(LoginProfessor loginProfessor, @PathVariable(value = "courseId") long courseId) {
        professorCourseCommandService.startCourse(loginProfessor.id(), courseId);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{courseId}/close")
    @Operation(
            summary = "수업 종료 상태로 변경",
            description = "courseId에 해당하는 수업을 종료 상태로 변경합니다.",
            responses = {@ApiResponse(responseCode = "204", description = "수업이 종료 상태로 변경되었습니다.")}
    )
    public ResponseEntity<Void> closeCourse(LoginProfessor loginProfessor, @PathVariable(value = "courseId") long courseId) {
        professorCourseCommandService.closeCourse(loginProfessor.id(), courseId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{courseId}/file")
    @Operation(
            summary = "수업 강의자료 파일 업로드",
            description = "courseId에 해당하는 수업에 강의자료 파일을 업로드합니다.",
            responses = {@ApiResponse(responseCode = "200", description = "수업 강의자료 파일이 업로드되었습니다.")}
    )
    public ResponseEntity<SuccessResponse<Map<String, String>>> uploadFile(
            LoginProfessor loginProfessor,
            @PathVariable(value = "courseId") long courseId,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        Map<String, String> response = professorCourseCommandService.uploadFile(loginProfessor.id(), courseId, file);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.of("수업 강의자료 파일 업로드에 성공했습니다.", response));
    }

    @GetMapping("/{courseId}/file")
    @Operation(
            summary = "수업 강의자료 파일 url 발급",
            description = "courseId에 해당하는 수업의 강의자료 파일을 다운로드 받을 수 있는 presigned-url을 발급합니다.",
            responses =
                    {@ApiResponse(responseCode = "200", description = "수업 강의자료 파일 url이 발급되었습니다.")}
    )
    public ResponseEntity<SuccessResponse<Map<String, String>>> getFile(
            LoginProfessor loginProfessor,
            @PathVariable(value = "courseId") long courseId) {
        Map<String, String> response = professorCourseCommandService.getCourseFileUrl(loginProfessor.id(), courseId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.of("수업 강의자료 파일 url을 발급했습니다.", response));
    }
}