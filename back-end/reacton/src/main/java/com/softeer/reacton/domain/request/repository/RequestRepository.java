package com.softeer.reacton.domain.request.repository;

import com.softeer.reacton.domain.course.entity.Course;
import com.softeer.reacton.domain.request.entity.Request;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RequestRepository extends JpaRepository<Request, Long> {
    void deleteAllByCourse(Course course);

    @Modifying
    @Query("DELETE FROM Request r WHERE r.course.id = :courseId")
    void deleteAllByCourseId(Long courseId);

    @Modifying
    @Transactional
    @Query("UPDATE Request r " +
            "SET r.count = r.count + 1 " +
            "WHERE r.course = :course " +
            "  AND r.type = :type")
    int incrementCount(@Param("course") Course course,
                       @Param("type") String type);

    @Modifying
    @Query("UPDATE Request r SET r.count = 0 WHERE r.course.id = :courseId")
    void resetCountByCourseId(@Param("courseId") Long courseId);
}
