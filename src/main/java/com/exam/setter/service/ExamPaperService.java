package com.exam.setter.service;

import com.exam.setter.dto.*;
import com.exam.setter.entity.ExamPaperEntity;
import com.exam.setter.entity.ExamSectionEntity;
import com.exam.setter.entity.QuestionEntity;
import com.exam.setter.entity.ExamProfileEntity;
import com.exam.setter.model.ModerationStatus;
import com.exam.setter.model.PaperStatus;
import com.exam.setter.repository.ExamPaperRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ExamAwareQuestionGenerationService examAwareGenerator;
    private final ExamPaperRepository examPaperRepository;
    private final ExamProfileService profileService;
    private final ObjectMapper objectMapper;

    public ExamPaperService(QuestionGeneratorService questionGeneratorService, ExamAwareQuestionGenerationService examAwareGenerator, ExamPaperRepository examPaperRepository, ExamProfileService profileService, ObjectMapper objectMapper) {
        this.questionGeneratorService = questionGeneratorService; this.examAwareGenerator = examAwareGenerator; this.examPaperRepository = examPaperRepository; this.profileService = profileService; this.objectMapper = objectMapper;
    }

    @Transactional
    public ExamPaperEntity assembleAndPersistExamPaper(ExamPaperBlueprintRequest request) {
        validateBlueprint(request); int computedTotalMarks = 0;
        ExamPaperEntity paperEntity = ExamPaperEntity.builder().title(request.examTitle()).subject(request.subject().trim().toLowerCase()).examId(request.examId() == null ? null : request.examId().trim().toUpperCase()).durationMinutes(request.durationMinutes()).status(PaperStatus.IN_REVIEW).createdAt(Instant.now()).build();
        List<ExamSectionEntity> sectionEntities = new ArrayList<>();
        for (SectionBlueprint section : request.sections()) {
            QuestionGenerationRequest genRequest = new QuestionGenerationRequest(request.subject(), request.targetLevels(), section.questionType(), section.difficulty(), section.questionCount(), section.marksPerQuestion(), request.examId(), section.topic(), request.knowledgeSource(), request.knowledgeSources(), request.corpusVersion(), request.ncertBookCode(), request.ncertChapterNumber());
            List<GeneratedQuestion> generatedQuestions = generate(genRequest); int sectionMarks = generatedQuestions.size() * section.marksPerQuestion(); computedTotalMarks += sectionMarks;
            ExamSectionEntity sectionEntity = ExamSectionEntity.builder().examPaper(paperEntity).sectionName(section.sectionName()).sectionMarks(sectionMarks).negativeMarks(section.negativeMarks()).build();
            List<QuestionEntity> questionEntities = generatedQuestions.stream().map(gq -> QuestionEntity.builder().section(sectionEntity).questionText(gq.questionText()).questionType(gq.questionType()).options(gq.options()).correctAnswer(gq.correctAnswer()).explanation(gq.explanation()).difficulty(gq.difficulty()).marks(gq.marks()).topic(gq.topic()).sourceCitationsJson(writeCitations(gq.sourceCitations())).moderationStatus(ModerationStatus.PENDING_REVIEW).includedInPaper(false).build()).toList();
            sectionEntity.setQuestions(new ArrayList<>(questionEntities)); sectionEntities.add(sectionEntity);
        }
        paperEntity.setTotalMarks(computedTotalMarks); paperEntity.setSections(sectionEntities); return examPaperRepository.save(paperEntity);
    }

    private List<GeneratedQuestion> generate(QuestionGenerationRequest request) { return request.knowledgeSources() != null && !request.knowledgeSources().isEmpty() ? examAwareGenerator.generate(request) : (request.examId() == null || request.examId().isBlank() ? questionGeneratorService.generateQuestions(request) : examAwareGenerator.generate(request)); }

    private void validateBlueprint(ExamPaperBlueprintRequest request) {
        if (request.sections().stream().mapToInt(SectionBlueprint::questionCount).sum() > 100) throw new IllegalArgumentException("A single paper may request at most 100 questions.");
        if ((request.knowledgeSources() == null || request.knowledgeSources().isEmpty()) && (request.knowledgeSource() == null || request.knowledgeSource().isBlank())) throw new IllegalArgumentException("Select at least one knowledge source before generating the paper.");
        if (request.examId() == null || request.examId().isBlank()) return;
        ExamProfileEntity profile = profileService.get(request.examId());
        if (profile.getSubject() != null && !profile.getSubject().isBlank() && !profile.getSubject().equalsIgnoreCase(request.subject())) throw new IllegalArgumentException("Selected exam profile is configured for subject " + profile.getSubject() + ".");
        List<String> profileLevels = readLevels(profile.getTargetLevelsJson());
        if (!profileLevels.isEmpty() && request.targetLevels() != null) { boolean compatible = request.targetLevels().stream().allMatch(level -> profileLevels.stream().anyMatch(p -> p.equalsIgnoreCase(level))); if (!compatible) throw new IllegalArgumentException("Blueprint target levels must be covered by the selected exam profile."); }
    }

    private List<String> readLevels(String json) { try { if (json == null || json.isBlank() || "null".equals(json)) return List.of(); return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)); } catch (Exception ignored) { return List.of(); } }
    private String writeCitations(List<String> citations) { try { return objectMapper.writeValueAsString(citations == null ? List.of() : citations); } catch (Exception ignored) { return "[]"; } }

    @Transactional
    public ExamPaperEntity updateQuestionSelection(java.util.UUID paperId, java.util.List<java.util.UUID> includedQuestionIds) { ExamPaperEntity paper = examPaperRepository.findById(paperId).orElseThrow(() -> new RuntimeException("Exam Paper not found: " + paperId)); Set<java.util.UUID> included = includedQuestionIds == null ? Set.of() : new HashSet<>(includedQuestionIds); paper.getSections().forEach(section -> section.getQuestions().forEach(question -> question.setIncludedInPaper(included.contains(question.getId())))); paper.setTotalMarks(paper.getSections().stream().flatMap(s -> s.getQuestions().stream()).filter(QuestionEntity::isIncludedInPaper).mapToInt(QuestionEntity::getMarks).sum()); paper.getSections().forEach(section -> section.setSectionMarks(section.getQuestions().stream().filter(QuestionEntity::isIncludedInPaper).mapToInt(QuestionEntity::getMarks).sum())); return examPaperRepository.save(paper); }

    public AssembledExamPaper assembleExamPaper(ExamPaperBlueprintRequest request) { validateBlueprint(request); int totalMarks = 0, totalQuestions = 0; List<AssembledExamPaper.AssembledSection> sections = new ArrayList<>(); for (SectionBlueprint section : request.sections()) { QuestionGenerationRequest genRequest = new QuestionGenerationRequest(request.subject(), request.targetLevels(), section.questionType(), section.difficulty(), section.questionCount(), section.marksPerQuestion(), request.examId(), section.topic(), request.knowledgeSource(), request.corpusVersion(), request.ncertBookCode(), request.ncertChapterNumber()); List<GeneratedQuestion> questions = generate(genRequest); int marks = questions.size() * section.marksPerQuestion(); totalMarks += marks; totalQuestions += questions.size(); sections.add(new AssembledExamPaper.AssembledSection(section.sectionName(), marks, section.negativeMarks(), questions)); } return new AssembledExamPaper(request.examTitle(), request.subject().trim().toLowerCase(), request.targetLevels(), request.durationMinutes(), totalMarks, totalQuestions, sections, Instant.now()); }
}
