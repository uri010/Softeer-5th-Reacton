package com.softeer.reacton.domain.professor.repository;

import com.softeer.reacton.domain.professor.entity.Professor;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {
    Optional<Professor> findByOauthId(String oauthId);

    @Modifying
    @Transactional
    @Query("UPDATE Professor p SET p.name = :newName WHERE p.id = :professorId")
    int updateName(@Param("professorId") Long professorId, @Param("newName") String newName);

    @Modifying
    @Transactional
    @Query("UPDATE Professor p SET p.profileImageFileName = :profileImageFileName, p.profileImageS3Key = :profileImageS3Key  WHERE p.id = :professorId")
    int updateImage(@Param("professorId") Long professorId, @Param("profileImageFileName") String profileImageFileName, @Param("profileImageS3Key") String profileImageS3Key);
}
