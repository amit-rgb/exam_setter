package com.exam.setter.repository;

import com.exam.setter.entity.PyqQuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PyqQuestionRepository extends JpaRepository<PyqQuestionEntity, UUID> {
    List<PyqQuestionEntity> findByExamIdIgnoreCaseOrderByYearAscQuestionNumberAsc(String examId);
    List<PyqQuestionEntity> findByExamIdIgnoreCaseAndSubjectIgnoreCase(String examId, String subject);
    List<PyqQuestionEntity> findBySubjectIgnoreCase(String subject);
}
