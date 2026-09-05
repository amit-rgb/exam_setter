package com.exam.setter.service;

import com.exam.setter.dto.ReviewActionRequest;
import com.exam.setter.entity.ExamPaperEntity;
import com.exam.setter.entity.QuestionEntity;
import com.exam.setter.model.ModerationStatus;
import com.exam.setter.model.PaperStatus;
import com.exam.setter.repository.ExamPaperRepository;
import com.exam.setter.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ExamModerationService {

    private final QuestionRepository questionRepository;
    private final ExamPaperRepository paperRepository;

    public ExamModerationService(QuestionRepository questionRepository, ExamPaperRepository paperRepository) {
        this.questionRepository = questionRepository;
        this.paperRepository = paperRepository;
    }

    @Transactional
    public QuestionEntity reviewQuestion(ReviewActionRequest request) {
        QuestionEntity question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> new RuntimeException("Question not found with ID: " + request.questionId()));

        // Inline corrections by teacher
        if (request.editedQuestionText() != null && !request.editedQuestionText().isBlank()) {
            question.setQuestionText(request.editedQuestionText());
        }
        if (request.editedOptions() != null && !request.editedOptions().isEmpty()) {
            question.setOptions(request.editedOptions());
        }

        // 1-Level Decision
        question.setModerationStatus(request.approved() ? ModerationStatus.APPROVED : ModerationStatus.REJECTED);
        question.setReviewerId(request.reviewerId());
        question.setReviewerComments(request.comments());
        question.setReviewedAt(Instant.now());

        return questionRepository.save(question);
    }

    @Transactional
    public ExamPaperEntity checkAndFinalizePaper(UUID paperId) {
        ExamPaperEntity paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new RuntimeException("Exam Paper not found: " + paperId));

        boolean hasPending = paper.getSections().stream()
                .flatMap(s -> s.getQuestions().stream())
                .anyMatch(q -> q.getModerationStatus() == ModerationStatus.PENDING_REVIEW);

        paper.setStatus(hasPending ? PaperStatus.IN_REVIEW : PaperStatus.APPROVED);

        // Recalculate marks and question count based on APPROVED questions only
        int finalMarks = paper.getSections().stream()
                .flatMap(s -> s.getQuestions().stream())
                .filter(q -> q.getModerationStatus() == ModerationStatus.APPROVED)
                .mapToInt(QuestionEntity::getMarks)
                .sum();

        paper.setTotalMarks(finalMarks);
        return paperRepository.save(paper);
    }
}