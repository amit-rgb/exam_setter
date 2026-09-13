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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ExamPaperService {

    private final QuestionGeneratorService questionGeneratorService;
    private final ExamPaperRepository examPaperRepository;

    public ExamPaperService(QuestionGeneratorService questionGeneratorService,
                            ExamPaperRepository examPaperRepository) {
        this.questionGeneratorService = questionGeneratorService;
        this.examPaperRepository = examPaperRepository;
    }

    @Transactional
    public ExamPaperEntity assembleAndPersistExamPaper(ExamPaperBlueprintRequest request) {
        validateBlueprint(request);
        int computedTotalMarks = 0;

        ExamPaperEntity paperEntity = ExamPaperEntity.builder()
                .title(request.examTitle())
                .subject(request.subject().trim().toLowerCase())
                .examId(request.examId() == null ? null : request.examId().trim().toUpperCase())
                .durationMinutes(request.durationMinutes())
                .status(PaperStatus.IN_REVIEW)
                .createdAt(Instant.now())
                .build();

        List<ExamSectionEntity> sectionEntities = new ArrayList<>();

        for (SectionBlueprint section : request.sections()) {
            QuestionGenerationRequest genRequest = new QuestionGenerationRequest(
                    request.subject(), request.targetLevels(), section.questionType(), section.difficulty(),
                    section.questionCount(), section.marksPerQuestion(), request.examId()
            );

            List<GeneratedQuestion> generatedQuestions = questionGeneratorService.generateQuestions(genRequest);
            int sectionMarks = generatedQuestions.size() * section.marksPerQuestion();
            computedTotalMarks += sectionMarks;

            ExamSectionEntity sectionEntity = ExamSectionEntity.builder()
                    .examPaper(paperEntity)
                    .sectionName(section.sectionName())
                    .sectionMarks(sectionMarks)
                    .negativeMarks(section.negativeMarks())
                    .build();

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
        return examPaperRepository.save(paperEntity);
    }

    private void validateBlueprint(ExamPaperBlueprintRequest request) {
        int requestedQuestionCount = request.sections().stream().mapToInt(SectionBlueprint::questionCount).sum();
        if (requestedQuestionCount > 100) {
            throw new IllegalArgumentException("A single paper may request at most 100 questions.");
        }
    }

    @Transactional
    public ExamPaperEntity updateQuestionSelection(java.util.UUID paperId, java.util.List<java.util.UUID> includedQuestionIds) {
        ExamPaperEntity paper = examPaperRepository.findById(paperId)
                .orElseThrow(() -> new RuntimeException("Exam Paper not found: " + paperId));
        Set<java.util.UUID> included = includedQuestionIds == null ? Set.of() : new HashSet<>(includedQuestionIds);

        paper.getSections().forEach(section -> section.getQuestions().forEach(question ->
                question.setIncludedInPaper(included.contains(question.getId()))));

        int selectedMarks = paper.getSections().stream()
                .flatMap(section -> section.getQuestions().stream())
                .filter(QuestionEntity::isIncludedInPaper)
                .mapToInt(QuestionEntity::getMarks).sum();
        paper.setTotalMarks(selectedMarks);

        paper.getSections().forEach(section -> section.setSectionMarks(
                section.getQuestions().stream().filter(QuestionEntity::isIncludedInPaper)
                        .mapToInt(QuestionEntity::getMarks).sum()));
        return examPaperRepository.save(paper);
    }

    public AssembledExamPaper assembleExamPaper(ExamPaperBlueprintRequest request) {
        int totalMarks = 0;
        int totalQuestions = 0;
        List<AssembledExamPaper.AssembledSection> assembledSections = new ArrayList<>();

        for (SectionBlueprint section : request.sections()) {
            QuestionGenerationRequest genRequest = new QuestionGenerationRequest(
                    request.subject(), request.targetLevels(), section.questionType(), section.difficulty(),
                    section.questionCount(), section.marksPerQuestion(), request.examId()
            );
            List<GeneratedQuestion> generatedQuestions = questionGeneratorService.generateQuestions(genRequest);
            int sectionMarks = generatedQuestions.size() * section.marksPerQuestion();
            totalMarks += sectionMarks;
            totalQuestions += generatedQuestions.size();
            assembledSections.add(new AssembledExamPaper.AssembledSection(
                    section.sectionName(), sectionMarks, section.negativeMarks(), generatedQuestions));
        }

        return new AssembledExamPaper(request.examTitle(), request.subject().trim().toLowerCase(),
                request.targetLevels(), request.durationMinutes(), totalMarks, totalQuestions,
                assembledSections, Instant.now());
    }
}
