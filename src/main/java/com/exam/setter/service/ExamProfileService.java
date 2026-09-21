package com.exam.setter.service;

import com.exam.setter.dto.ExamProfileRequest;
import com.exam.setter.entity.ExamProfileEntity;
import com.exam.setter.model.KnowledgeSource;
import com.exam.setter.repository.ExamProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExamProfileService {
    private final ExamProfileRepository repository;
    private final ObjectMapper objectMapper;

    public ExamProfileService(ExamProfileRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExamProfileEntity save(ExamProfileRequest request) {
        validateDistribution("difficultyDistribution", request.difficultyDistribution());
        validateDistribution("questionTypeDistribution", request.questionTypeDistribution());
        validateDistribution("topicDistribution", request.topicDistribution());

        String knowledgeSource = normalizeKnowledgeSource(request.knowledgeSource());
        if (knowledgeSource.equals(KnowledgeSource.NCERT.name()) || knowledgeSource.equals(KnowledgeSource.MIXED.name())) {
            if (request.ncertChapterNumber() != null && request.ncertChapterNumber() < 1) {
                throw new IllegalArgumentException("NCERT chapter number must be positive.");
            }
        }

        ExamProfileEntity entity = repository.findByExamIdIgnoreCase(request.examId())
                .orElseGet(ExamProfileEntity::new);
        Instant now = Instant.now();
        if (entity.getCreatedAt() == null) entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setExamId(request.examId().trim().toUpperCase());
        entity.setExamName(request.examName().trim());
        entity.setSubject(blankToNull(request.subject()));
        entity.setPaperName(blankToNull(request.paperName()));
        entity.setKnowledgeSource(knowledgeSource);
        entity.setCorpusVersion(blankToNull(request.corpusVersion()));
        entity.setNcertBookCode(blankToNull(request.ncertBookCode()));
        entity.setNcertChapterNumber(request.ncertChapterNumber());
        entity.setTargetLevelsJson(write(request.targetLevels()));
        entity.setQuestionCount(request.questionCount());
        entity.setMarksPerQuestion(request.marksPerQuestion());
        entity.setDurationMinutes(request.durationMinutes());
        entity.setQuestionTypesJson(write(request.questionTypes()));
        entity.setDifficultyDistributionJson(write(request.difficultyDistribution()));
        entity.setQuestionTypeDistributionJson(write(request.questionTypeDistribution()));
        entity.setTopicDistributionJson(write(request.topicDistribution()));
        entity.setInstructions(request.instructions());
        entity.setPreviousYearRange(request.previousYearRange());
        return repository.save(entity);
    }

    public ExamProfileEntity get(String examId) {
        return repository.findByExamIdIgnoreCase(examId)
                .orElseThrow(() -> new IllegalArgumentException("Exam profile not found: " + examId));
    }

    public List<ExamProfileEntity> list() { return repository.findAll(); }

    @Transactional
    public void delete(UUID id) { repository.deleteById(id); }

    public Map<String, Object> toGenerationConstraints(ExamProfileEntity profile) {
        return Map.ofEntries(
                Map.entry("examId", profile.getExamId()),
                Map.entry("examName", profile.getExamName()),
                Map.entry("subject", nullSafe(profile.getSubject())),
                Map.entry("paperName", nullSafe(profile.getPaperName())),
                Map.entry("knowledgeSource", blankToDefault(profile.getKnowledgeSource(), "NCERT")),
                Map.entry("corpusVersion", nullSafe(profile.getCorpusVersion())),
                Map.entry("ncertBookCode", nullSafe(profile.getNcertBookCode())),
                Map.entry("ncertChapterNumber", nullSafe(profile.getNcertChapterNumber())),
                Map.entry("targetLevels", readList(profile.getTargetLevelsJson())),
                Map.entry("questionCount", nullSafe(profile.getQuestionCount())),
                Map.entry("marksPerQuestion", nullSafe(profile.getMarksPerQuestion())),
                Map.entry("durationMinutes", nullSafe(profile.getDurationMinutes())),
                Map.entry("questionTypes", readList(profile.getQuestionTypesJson())),
                Map.entry("difficultyDistribution", readMap(profile.getDifficultyDistributionJson())),
                Map.entry("questionTypeDistribution", readMap(profile.getQuestionTypeDistributionJson())),
                Map.entry("topicDistribution", readMap(profile.getTopicDistributionJson())),
                Map.entry("previousYearRange", nullSafe(profile.getPreviousYearRange())),
                Map.entry("instructions", nullSafe(profile.getInstructions()))
        );
    }

    private String normalizeKnowledgeSource(String value) {
        String normalized = blankToDefault(value, KnowledgeSource.NCERT.name());
        try {
            return KnowledgeSource.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported knowledge source: " + value + ". Use NCERT, MIXED or USER_UPLOAD.");
        }
    }

    private void validateDistribution(String name, Map<String, Double> distribution) {
        if (distribution == null || distribution.isEmpty()) return;
        double total = 0;
        for (Map.Entry<String, Double> entry : distribution.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 100) {
                throw new IllegalArgumentException(name + " values must be between 0 and 100.");
            }
            total += entry.getValue();
        }
        if (Math.abs(total - 100.0) > 0.5) {
            throw new IllegalArgumentException(name + " must total 100%. Current total: " + total);
        }
    }

    private String write(Object value) {
        try { return value == null ? "null" : objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid exam profile JSON", e); }
    }

    private List<String> readList(String json) {
        try { return json == null ? List.of() : objectMapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private Map<String, Double> readMap(String json) {
        try { return json == null ? Map.of() : objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {}); }
        catch (Exception e) { return Map.of(); }
    }

    private Object nullSafe(Object value) { return value == null ? "" : value; }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String blankToDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(); }
}
