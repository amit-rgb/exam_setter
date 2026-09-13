package com.exam.setter.repository;

import com.exam.setter.entity.ExamProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExamProfileRepository extends JpaRepository<ExamProfileEntity, UUID> {
    Optional<ExamProfileEntity> findByExamIdIgnoreCase(String examId);
}
