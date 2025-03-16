package com.softeer.reacton.domain.professor.controller;

import com.softeer.reacton.domain.professor.service.ProfessorService;
import com.softeer.reacton.domain.professor.dto.ProfessorInfoResponse;
import com.softeer.reacton.domain.professor.dto.UpdateNameRequest;
import com.softeer.reacton.global.config.CookieConfig;
import com.softeer.reacton.global.dto.SuccessResponse;
import com.softeer.reacton.global.jwt.dto.ProfessorAuthInfo;
import com.softeer.reacton.global.jwt.dto.ProfessorSignupInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/professors")
@Tag(name = "Professor API", description = "교수 사용자 관련 API")
@RequiredArgsConstructor
@Validated
public class ProfessorController {

    private final ProfessorService professorService;
    private final CookieConfig cookieConfig;

    @GetMapping
    @Operation(
            summary = "교수 프로필 정보 조회",
            description = "교수의 이름과 이메일, 프로필 이미지 url을 가져옵니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "성공적으로 조회했습니다."),
                    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다."),
                    @ApiResponse(responseCode = "500", description = "서버와의 연결에 실패했습니다.")
            }
    )
    public ResponseEntity<SuccessResponse<ProfessorInfoResponse>> getProfileInfo(ProfessorAuthInfo professorAuthInfo) {
        ProfessorInfoResponse response = professorService.getProfileInfo(professorAuthInfo.id());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.of("성공적으로 조회했습니다.", response));
    }

    @GetMapping("/image")
    @Operation(
            summary = "교수 프로필 이미지 조회",
            description = "교수의 프로필 이미지 정보를 가져옵니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "성공적으로 조회했습니다."),
                    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다."),
                    @ApiResponse(responseCode = "500", description = "서버와의 연결에 실패했습니다.")
            }
    )
    public ResponseEntity<SuccessResponse<Map<String, String>>> getProfileImage(ProfessorAuthInfo professorAuthInfo) {
        Map<String, String> response = professorService.getProfileImage(professorAuthInfo.id());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.of("성공적으로 조회했습니다.", response));
    }

    @PatchMapping("/name")
    @Operation(
            summary = "사용자 이름 변경",
            description = "사용자의 이름을 변경합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "성공적으로 변경되었습니다."),
                    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다."),
                    @ApiResponse(responseCode = "500", description = "서버와의 연결에 실패했습니다.")
            }
    )
    public ResponseEntity<SuccessResponse<Map<String, String>>> updateName(
            ProfessorAuthInfo professorAuthInfo,
            @Valid @RequestBody UpdateNameRequest requestDto) {
        Map<String, String> response = professorService.updateName(professorAuthInfo.id(), requestDto.getName());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.of("이름을 성공적으로 변경했습니다.", response));
    }


    @PatchMapping("/image")
    @Operation(
            summary = "사용자 프로필 이미지 변경",
            description = "사용자의 프로필 이미지를 변경합니다.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "성공적으로 변경되었습니다."),
                    @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다."),
                    @ApiResponse(responseCode = "500", description = "서버와의 연결에 실패했습니다.")
            }
    )
    public ResponseEntity<SuccessResponse<Map<String, String>>> updateImage(
            ProfessorAuthInfo professorAuthInfo,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImageFile) {
        Map<String, String> imageUrl = professorService.updateImage(professorAuthInfo.id(), profileImageFile);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.of("사용자의 프로필 이미지를 성공적으로 변경했습니다.", imageUrl));
    }

    @PostMapping("/signup")
    @Operation(
            summary = "사용자 등록",
            description = "사용자 정보를 기반으로 회원가입 과정을 수행합니다.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "성공적으로 생성되었습니다."),
                    @ApiResponse(responseCode = "409", description = "이미 가입된 사용자입니다."),
                    @ApiResponse(responseCode = "500", description = "서버와의 연결에 실패했습니다.")
            }
    )
    public ResponseEntity<Void> signUp(
            ProfessorSignupInfo signTokenInfo,
            @RequestPart("name") @Pattern(regexp = "^[가-힣a-zA-Z]{1,20}$", message = "이름은 한글 또는 영문만 1~20자 입력 가능합니다.") String name,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImageFile) {
        String newAccessToken = professorService.signUp(name, profileImageFile, signTokenInfo.oauthId(), signTokenInfo.email(), signTokenInfo.isSignedUp());

        ResponseCookie expiredSignupCookie = ResponseCookie.from("signup_token", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        ResponseCookie jwtCookie = ResponseCookie.from("access_token", newAccessToken)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(cookieConfig.getAuthExpiration())
                .sameSite("Strict")
                .build();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .header(HttpHeaders.SET_COOKIE, expiredSignupCookie.toString())
                .build();
    }

    @PostMapping("/logout")
    @Operation(
            summary = "사용자 로그아웃",
            description = "사용자 로그아웃을 수행합니다.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "성공적으로 로그아웃되었습니다."),
            }
    )
    public ResponseEntity<Void> logout() {
        ResponseCookie jwtCookie = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        return ResponseEntity
                .status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .build();
    }

    @DeleteMapping
    @Operation(
            summary = "사용자 탈퇴",
            description = "사용자 탈퇴를 수행합니다.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "성공적으로 탈퇴되었습니다."),
            }
    )
    public ResponseEntity<Void> delete(ProfessorAuthInfo professorAuthInfo) {
        professorService.delete(professorAuthInfo.id());

        ResponseCookie jwtCookie = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        return ResponseEntity
                .status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .build();
    }
}
