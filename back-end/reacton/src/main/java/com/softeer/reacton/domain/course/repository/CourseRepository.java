package com.softeer.reacton.domain.course.repository;

import com.softeer.reacton.domain.course.entity.Course;
import com.softeer.reacton.domain.professor.entity.Professor;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    @Query("SELECT DISTINCT c FROM Course c " +
            "LEFT JOIN FETCH c.schedules s " +
            "WHERE c.professor.id = :professorId " +
            "ORDER BY c.createdAt DESC, " +
            "CASE s.day " +
            "WHEN '월' THEN 1 " +
            "WHEN '화' THEN 2 " +
            "WHEN '수' THEN 3 " +
            "WHEN '목' THEN 4 " +
            "WHEN '금' THEN 5 " +
            "WHEN '토' THEN 6 " +
            "WHEN '일' THEN 7 " +
            "ELSE 8 END")
    List<Course> findCoursesWithSchedulesByProfessorId(@Param("professorId") Long professorId);

    @Query("SELECT DISTINCT c FROM Course c " +
            "LEFT JOIN FETCH c.schedules s " +
            "WHERE c.professor.id = :professorId " +
            "AND (LOWER(c.name) LIKE :keyword ESCAPE '\\' OR LOWER(c.courseCode) LIKE :keyword ESCAPE '\\') " +
            "ORDER BY c.createdAt DESC, " +
            "CASE s.day " +
            "WHEN '월' THEN 1 " +
            "WHEN '화' THEN 2 " +
            "WHEN '수' THEN 3 " +
            "WHEN '목' THEN 4 " +
            "WHEN '금' THEN 5 " +
            "WHEN '토' THEN 6 " +
            "WHEN '일' THEN 7 " +
            "ELSE 8 END")
    List<Course> findCoursesWithSchedulesByProfessorAndKeyword(@Param("professorId") Long professorId, @Param("keyword") String keyword);

    Optional<Course> findByAccessCode(int accessCode);

    List<Course> findByProfessor(Professor professor);

    void deleteByProfessor(Professor professor);

    @Query("SELECT c FROM Course c WHERE c.professor.id = :professorId AND c.isActive = true ORDER BY c.id DESC")
    List<Course> findLatestActiveCourseByProfessorId(@Param("professorId") Long professorId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Course c WHERE c.id = :courseId")
    void deleteByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT c FROM Course c WHERE c.id = :courseId AND c.professor.id = :professorId")
    Optional<Course> findByIdAndProfessorId(long courseId, Long professorId);

    @Query("SELECT COUNT(c) > 0 FROM Course c WHERE c.id = :courseId AND c.professor.id = :professorId")
    boolean existsByIdAndProfessorId(long courseId, Long professorId);

    @Transactional
    @Modifying
    @Query("UPDATE Course c SET c.isActive = false WHERE c.professor.id = :professorId AND c.id <> :courseId")
    void deactivateOtherCourses(@Param("professorId") Long professorId, @Param("courseId") Long courseId);

}
