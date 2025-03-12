package com.softeer.reacton.domain.course;

import com.softeer.reacton.domain.course.dto.*;
import com.softeer.reacton.domain.professor.Professor;
import com.softeer.reacton.domain.professor.ProfessorRepository;
import com.softeer.reacton.domain.professor.ProfessorService;
import com.softeer.reacton.domain.question.QuestionRepository;
import com.softeer.reacton.domain.question.QuestionService;
import com.softeer.reacton.domain.request.Request;
import com.softeer.reacton.domain.request.RequestService;
import com.softeer.reacton.domain.schedule.Schedule;
import com.softeer.reacton.domain.schedule.ScheduleRepository;
import com.softeer.reacton.domain.schedule.ScheduleService;
import com.softeer.reacton.global.exception.BaseException;
import com.softeer.reacton.global.exception.code.CourseErrorCode;
import com.softeer.reacton.global.exception.code.FileErrorCode;
import com.softeer.reacton.global.exception.code.ProfessorErrorCode;
import com.softeer.reacton.global.s3.S3Service;
import com.softeer.reacton.global.sse.SseMessageSender;
import com.softeer.reacton.global.sse.dto.SseMessage;
import com.softeer.reacton.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfessorCourseService {

    private final ProfessorRepository professorRepository;
    private final CourseRepository courseRepository;
    private final ScheduleRepository scheduleRepository;
    private final QuestionRepository questionRepository;

    private final ProfessorService professorService;
    private final ScheduleService scheduleService;
    private final QuestionService questionService;
    private final RequestService requestService;

    private final ProfessorCourseTransactionService professorCourseTransactionService;
    private final S3Service s3Service;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final int MAX_RETRIES = 10;
    private static final String FILE_DIRECTORY = "course-files/";
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;
    private static final int PRESIGNED_URL_EXPIRATION_MINUTES = 1;
    private final SseMessageSender sseMessageSender;

    public ActiveCourseResponse getActiveCourseByUser(String oauthId) {
        log.debug("활성화된 수업을 조회합니다.");

        Long professorId = professorService.getProfessorIdByOauthId(oauthId);
        Course course = courseRepository.findTopByProfessorIdAndIsActiveTrue(professorId).orElse(null);

        if (course != null) {
            List<CourseScheduleResponse> schedules = scheduleService.getSchedulesByCourseInOrder(course);
            return ActiveCourseResponse.of(course, schedules);
        }
        return null;
    }

    @Transactional
    public long createCourse(String oauthId, CourseRequest request) {
        log.debug("수업을 생성합니다.");

        Professor professor = professorService.getProfessorByOauthId(oauthId);

        Course course = Course.create(request, professor);

        List<Schedule> schedules = scheduleService.createSchedules(request, course);
        course.setSchedules(schedules);

        List<Request> requests = requestService.createRequests(course);
        course.setRequests(requests);

        return generateAccessCodeAndSave(course);
    }

    public CourseDetailResponse getCourseDetail(long courseId, String oauthId) {
        log.debug("수업 상세 정보를 조회합니다.");

        Long professorId = professorService.getProfessorIdByOauthId(oauthId);
        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow( () -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        List<CourseScheduleResponse> schedules = scheduleService.getSchedulesByCourseInOrder(course);
        List<CourseQuestionResponse> questions = questionService.getQuestionsByCourseInOrder(course);
        List<CourseRequestResponse> requests = requestService.getRequestsByCourseInOrder(course);

        log.debug("수업 상세 정보를 가져오는 데 성공했습니다. : courseId = {}", courseId);
        return CourseDetailResponse.of(course, schedules, questions, requests);
    }

    public CourseAllResponse getAllCourses(String oauthId) {
        log.debug("전체 수업 목록을 조회합니다.");

        Long professorId = professorService.getProfessorIdByOauthId(oauthId);

        List<Course> allCourses = courseRepository.findCoursesWithSchedulesByProfessorId(professorId);
        List<CourseSummaryResponse> todayCoursesResponse = getTodayCoursesResponse(allCourses);
        List<CourseSummaryResponse> allCoursesResponse = getAllCoursesResponse(allCourses);

        return CourseAllResponse.of(todayCoursesResponse, allCoursesResponse);
    }

    public List<CourseSummaryResponse> searchCourses(String oauthId, String keyword) {
        log.debug("검색 결과를 조회합니다.");

        Long professorId = professorService.getProfessorIdByOauthId(oauthId);
        List<Course> searchCourses;
        if (keyword == null || keyword.isEmpty()) {
            searchCourses = courseRepository.findCoursesWithSchedulesByProfessorId(professorId);
        } else {
            String escapedKeyword = escapeWildcard(keyword);
            String searchKeyword = "%" + escapedKeyword + "%";
            searchCourses = courseRepository.findCoursesWithSchedulesByProfessorAndKeyword(professorId, searchKeyword);
        }

        return getAllCoursesResponse(searchCourses);
    }

    @Transactional
    public void updateCourse(String oauthId, long courseId, CourseRequest request) {
        log.debug("수업 데이터를 업데이트합니다. : courseId = {}", courseId);

        if (request == null) {
            log.warn("수업 수정 요청 데이터가 null입니다. : 'request' is null.");
            throw new BaseException(CourseErrorCode.COURSE_REQUEST_IS_NULL);
        }

        Long professorId = professorService.getProfessorIdByOauthId(oauthId);
        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow( () -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        course.update(request);

        List<CourseRequest.ScheduleRequest> scheduleRequests = request.getSchedules();
        scheduleRepository.deleteAllByCourse(course);
        course.getSchedules().clear(); // 영속성 컨텍스트에서도 제거

        List<Schedule> newSchedules = scheduleRequests.stream()
                .map(scheduleRequest -> Schedule.create(scheduleRequest, course))
                .collect(Collectors.toList());
        scheduleRepository.saveAll(newSchedules);
        course.setSchedules(newSchedules);

        log.info("수업 업데이트가 완료되었습니다. : courseId = {}", courseId);
    }

    @Transactional
    public void deleteCourse(String oauthId, long courseId) {
        log.debug("수업을 삭제합니다. : courseId = {}", courseId);

        Long professorId = professorService.getProfessorIdByOauthId(oauthId);

        if (!courseRepository.existsByIdAndProfessorId(courseId, professorId)) {
            throw new BaseException(CourseErrorCode.COURSE_NOT_FOUND);
        }

        scheduleService.deleteAllByCourseId(courseId);
        questionService.deleteAllByCourseId(courseId);
        requestService.deleteAllByCourseId(courseId);

        courseRepository.deleteByCourseId(courseId);

        log.info("수업이 삭제되었습니다. : courseId = {}", courseId);
    }

    public void startCourse(String oauthId, long courseId) {
        log.debug("수업을 시작 상태로 변경합니다. courseId = {}", courseId);


        Long professorId = professorService.getProfessorIdByOauthId(oauthId);
        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow( () -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        boolean wasActive = course.isActive();
        log.debug("시작을 요청한 수업의 활성화 상태입니다. : isActive = {}", wasActive);

        deactivateOtherCourses(oauthId, courseId);
        if( !wasActive ) {
            professorCourseTransactionService.activateCourse(course);
        }
        log.info("수업이 시작 상태로 변경되었습니다. courseId = {}", courseId);
    }

    @Transactional
    public void closeCourse(String oauthId, long courseId) {
        log.debug("수업을 종료 상태로 변경합니다. courseId = {}", courseId);

        Long professorId = professorService.getProfessorIdByOauthId(oauthId);
        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow( () -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        course.deactivate();
        questionRepository.deleteCompleteByCourse(course);

        log.debug("SSE 서버에 수업 종료 메시지 전송을 요청합니다.");
        SseMessage<Void> sseMessage = new SseMessage<>("COURSE_CLOSED", null);
        sseMessageSender.sendMessageToAll(String.valueOf(courseId), sseMessage);

        log.info("수업이 종료 상태로 변경되었습니다. courseId = {}", courseId);
    }

    @Transactional
    public Map<String, String> uploadFile(String oauthId, long courseId, MultipartFile file) {
        Long professorId = professorService.getProfessorIdByOauthId(oauthId);
        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow( () -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        deleteExistingFileIfExists(course);

        String fileName = null;
        String s3Key = null;
        if (validateFile(file)) {
            fileName = file.getOriginalFilename();
            s3Key = s3Service.uploadFile(file, FILE_DIRECTORY);
        } else {
            log.debug("요청에 파일이 존재하지 않으므로 기존에 저장된 파일만 삭제합니다.");
        }

        course.setFileName(fileName);
        course.setFileS3Key(s3Key);
        courseRepository.save(course);

        return Map.of("fileName", fileName != null ? fileName : "");
    }

    public Map<String, String> getCourseFileUrl(String oauthId, long courseId) {
        Long professorId = professorService.getProfessorIdByOauthId(oauthId);
        Course course = courseRepository.findByIdAndProfessorId(courseId, professorId)
                .orElseThrow( () -> new BaseException(CourseErrorCode.COURSE_NOT_FOUND));

        if (isFileExists(course)) {
            String s3Url = s3Service.generatePresignedUrl(course.getFileS3Key(), PRESIGNED_URL_EXPIRATION_MINUTES).toString();
            return Map.of("fileUrl", s3Url);
        }
        return Map.of("fileUrl", "");
    }

    private void deactivateOtherCourses(String oauthId, long courseId) {
        Long professorId = professorService.getProfessorIdByOauthId(oauthId);
        List<Course> activeCourses = courseRepository.findIsActiveCoursesByProfessorId(professorId);

        for (Course course : activeCourses) {
            if (course.getId() != courseId) {
                professorCourseTransactionService.closeCourse(course);
            }
        }

        courseRepository.saveAll(activeCourses);
    }

    private List<CourseScheduleResponse> getSchedulesByCourse(Course course) {
        return course.getSchedules().stream()
                .map(schedule -> new CourseScheduleResponse(
                        schedule.getDay(),
                        schedule.getStartTime().toString(),
                        schedule.getEndTime().toString()))
                .collect(Collectors.toList());
    }

    private List<CourseSummaryResponse> getTodayCoursesResponse(List<Course> allCourses) {
        String currentDay = TimeUtil.getCurrentDay();

        return allCourses.stream()
                .filter(course -> hasScheduleInDay(course, currentDay))
                .sorted(Comparator
                        .comparing(course -> getEarliestStartTime(course, currentDay)))
                .map(course -> CourseSummaryResponse.of(course, getSchedulesByCourse(course)))
                .collect(Collectors.toList());
    }

    private List<CourseSummaryResponse> getAllCoursesResponse(List<Course> allCourses) {
        return allCourses.stream()
                .map(course -> CourseSummaryResponse.of(course, getSchedulesByCourse(course)))
                .collect(Collectors.toList());
    }

    private boolean hasScheduleInDay(Course course, String day) {
        return course.getSchedules().stream()
                .anyMatch(schedule -> schedule.getDay().equals(day));
    }

    private LocalTime getEarliestStartTime(Course course, String day) {
        return course.getSchedules().stream()
                .filter(schedule -> schedule.getDay().equals(day))
                .map(Schedule::getStartTime)
                .min(Comparator.naturalOrder())
                .orElse(LocalTime.MAX);
    }

    private long generateAccessCodeAndSave(Course course) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            int accessCode = generateUniqueAccessCode();
            log.debug("입장 코드 생성 시도 {}회 - {}", i + 1, accessCode);

            course.setAccessCode(accessCode);

            try {
                return professorCourseTransactionService.saveCourse(course);
            } catch (DataIntegrityViolationException e) {
                log.warn("입장 코드 중복으로 인해 저장 실패 - 재시도 {}회: {}", i + 1, accessCode);
            }
        }

        log.error("최대 시도 횟수({}) 초과로 인해 입장 코드 생성 실패", MAX_RETRIES);
        throw new BaseException(CourseErrorCode.ACCESS_CODE_GENERATION_FAILED);
    }

    private int generateUniqueAccessCode() {
        return 100000 + secureRandom.nextInt(900000); // 100000~999999
    }

    private boolean validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BaseException(FileErrorCode.FILE_SIZE_EXCEEDED);
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new BaseException(FileErrorCode.INVALID_FILE_TYPE);
        }

        String mimeType = file.getContentType();
        if (mimeType == null || !mimeType.equals("application/pdf")) {
            throw new BaseException(FileErrorCode.INVALID_FILE_TYPE);
        }

        return true;
    }

    private boolean isFileExists(Course course) {
        return course.getFileName() != null && !course.getFileName().isEmpty() &&
                course.getFileS3Key() != null && !course.getFileS3Key().isEmpty();
    }

    private void deleteExistingFileIfExists(Course course) {
        if (isFileExists(course)) {
            s3Service.deleteFile(course.getFileS3Key());
            log.debug("기존 강의자료 파일 삭제 완료: fileName = {}", course.getFileName());
        }
    }

    private String escapeWildcard(String keyword) {
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}