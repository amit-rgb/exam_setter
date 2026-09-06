package com.exam.setter.service;

import com.exam.setter.dto.*;
import com.exam.setter.entity.ExamPaperEntity;
import com.exam.setter.entity.ExamSectionEntity;
import com.exam.setter.entity.QuestionEntity;
import com.exam.setter.model.ModerationStatus;
import com.exam.setter.model.PaperStatus;
import com.exam.setter.repository.ExamPaperRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Service
public class ExamPaperService {

    private final QuestionGeneratorService questionGeneratorService;
    private final ExamPaperRepository examPaperRepository;

    public ExamPaperService(QuestionGeneratorService questionGeneratorService,
                            ExamPaperRepository examPaperRepository) {
        this.questionGeneratorService = questionGeneratorService;
        this.examPaperRepository = examPaperRepository;
    }

    /**
     * Generates questions via RAG and persists them into the database
     * with PENDING_REVIEW status for single-level expert moderation.
     */
    @Transactional
    public ExamPaperEntity assembleAndPersistExamPaper(ExamPaperBlueprintRequest request) {
        validateBlueprint(request);
        int computedTotalMarks = 0;

        ExamPaperEntity paperEntity = ExamPaperEntity.builder()
                .title(request.examTitle())
                .subject(request.subject().trim().toLowerCase())
                .durationMinutes(request.durationMinutes())
                .status(PaperStatus.IN_REVIEW)
                .createdAt(Instant.now())
                .build();

        List<ExamSectionEntity> sectionEntities = new ArrayList<>();

        for (SectionBlueprint section : request.sections()) {
            QuestionGenerationRequest genRequest = new QuestionGenerationRequest(
                    request.subject(),
                    request.targetLevels(),
                    section.questionType(),
                    section.difficulty(),
                    section.questionCount(),
                    section.marksPerQuestion()
            );

            // 1. Generate grounded questions from vector store context
            List<GeneratedQuestion> generatedQuestions = questionGeneratorService.generateQuestions(genRequest);

            int sectionMarks = generatedQuestions.size() * section.marksPerQuestion();
            computedTotalMarks += sectionMarks;

            ExamSectionEntity sectionEntity = ExamSectionEntity.builder()
                    .examPaper(paperEntity)
                    .sectionName(section.sectionName())
                    .sectionMarks(sectionMarks)
                    .negativeMarks(section.negativeMarks())
                    .build();

            // 2. Map DTOs to JPA entities with default PENDING_REVIEW state
            List<QuestionEntity> questionEntities = generatedQuestions.stream().map(gq ->
                    QuestionEntity.builder()
                            .section(sectionEntity)
                            .questionText(gq.questionText())
                            .questionType(gq.questionType())
                            .options(gq.options())
                            .correctAnswer(gq.correctAnswer())
                            .explanation(gq.explanation())
                            .difficulty(gq.difficulty())
                            .marks(gq.marks())
                            .topic(gq.topic())
                            .moderationStatus(ModerationStatus.PENDING_REVIEW)
                            .includedInPaper(false)
                            .build()
            ).toList();

            sectionEntity.setQuestions(new ArrayList<>(questionEntities));
            sectionEntities.add(sectionEntity);
        }

        paperEntity.setTotalMarks(computedTotalMarks);
        paperEntity.setSections(sectionEntities);

        // 3. Persist cascade tree: ExamPaper -> ExamSections -> Questions
        return examPaperRepository.save(paperEntity);
    }

    private void validateBlueprint(ExamPaperBlueprintRequest request) {
        int requestedQuestionCount = request.sections().stream()
                .mapToInt(SectionBlueprint::questionCount)
                .sum();

        if (requestedQuestionCount > 100) {
            throw new IllegalArgumentException("A single paper may request at most 100 questions.");
        }

    }

    @Transactional
    public ExamPaperEntity updateQuestionSelection(java.util.UUID paperId, java.util.List<java.util.UUID> includedQuestionIds) {
        ExamPaperEntity paper = examPaperRepository.findById(paperId)
                .orElseThrow(() -> new RuntimeException("Exam Paper not found: " + paperId));

        java.util.Set<java.util.UUID> included = includedQuestionIds == null
                ? Set.of()
                : new HashSet<>(includedQuestionIds);

        paper.getSections().forEach(section ->
                section.getQuestions().forEach(question ->
                        question.setIncludedInPaper(included.contains(question.getId()))
                )
        );

        int selectedMarks = paper.getSections().stream()
                .flatMap(section -> section.getQuestions().stream())
                .filter(QuestionEntity::isIncludedInPaper)
                .mapToInt(QuestionEntity::getMarks)
                .sum();
        paper.setTotalMarks(selectedMarks);

        paper.getSections().forEach(section -> {
            int sectionMarks = section.getQuestions().stream()
                    .filter(QuestionEntity::isIncludedInPaper)
                    .mapToInt(QuestionEntity::getMarks)
                    .sum();
            section.setSectionMarks(sectionMarks);
        });

        return examPaperRepository.save(paper);
    }

    /**
     * Assemble an in-memory AssembledExamPaper DTO without persisting to the database.
     * This is used by endpoints that only need a generated preview/export.
     */
    public com.exam.setter.dto.AssembledExamPaper assembleExamPaper(ExamPaperBlueprintRequest request) {
        int totalMarks = 0;
        int totalQuestions = 0;

        List<com.exam.setter.dto.AssembledExamPaper.AssembledSection> assembledSections = new ArrayList<>();

        for (SectionBlueprint section : request.sections()) {
            QuestionGenerationRequest genRequest = new QuestionGenerationRequest(
                    request.subject(),
                    request.targetLevels(),
                    section.questionType(),
                    section.difficulty(),
                    section.questionCount(),
                    section.marksPerQuestion()
            );

            List<GeneratedQuestion> generatedQuestions = questionGeneratorService.generateQuestions(genRequest);

            int sectionMarks = generatedQuestions.size() * section.marksPerQuestion();
            totalMarks += sectionMarks;
            totalQuestions += generatedQuestions.size();

            com.exam.setter.dto.AssembledExamPaper.AssembledSection assembledSection =
                    new com.exam.setter.dto.AssembledExamPaper.AssembledSection(
                            section.sectionName(),
                            sectionMarks,
                            section.negativeMarks(),
                            generatedQuestions
                    );

            assembledSections.add(assembledSection);
        }

        return new com.exam.setter.dto.AssembledExamPaper(
                request.examTitle(),
                request.subject().trim().toLowerCase(),
                request.targetLevels(),
                request.durationMinutes(),
                totalMarks,
                totalQuestions,
                assembledSections,
                Instant.now()
        );
    }
}