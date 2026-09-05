package com.exam.setter.repository;

import com.exam.setter.entity.ExamPaperEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExamPaperRepository extends JpaRepository<ExamPaperEntity, UUID> {
}