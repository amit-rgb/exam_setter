package com.exam.setter.service;

import com.exam.setter.dto.ExamPaperResponse;
import com.exam.setter.dto.GeneratedQuestion;
import com.exam.setter.dto.QuestionGenerationRequest;
import com.exam.setter.entity.ExamProfileEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExamAwareQuestionGenerationService {
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final VectorMetadataHelperService metadataHelper;
    private final NcertRetrievalService ncertRetrieval;
    private final ExamProfileService profileService;
    private final QuestionSimilarityGuardService similarityGuard;
    private final ObjectMapper mapper;

    public ExamAwareQuestionGenerationService(ChatClient.Builder builder, VectorStore vectorStore, VectorMetadataHelperService metadataHelper, NcertRetrievalService ncertRetrieval, ExamProfileService profileService, QuestionSimilarityGuardService similarityGuard, ObjectMapper mapper) {
        this.chatClient = builder.build(); this.vectorStore = vectorStore; this.metadataHelper = metadataHelper; this.ncertRetrieval = ncertRetrieval; this.profileService = profileService; this.similarityGuard = similarityGuard; this.mapper = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public List<GeneratedQuestion> generate(QuestionGenerationRequest request) {
        ExamProfileEntity profile = profileService.get(request.examId());
        String source = firstNonBlank(request.knowledgeSource(), profile.getKnowledgeSource(), "NCERT");
        String corpusVersion = firstNonBlank(request.corpusVersion(), profile.getCorpusVersion(), null);
        String bookCode = firstNonBlank(request.bookCode(), profile.getNcertBookCode(), null);
        Integer chapterNumber = request.chapterNumber() != null ? request.chapterNumber() : profile.getNcertChapterNumber();
        List<Document> knowledge = retrieveKnowledge(request, source, corpusVersion, bookCode, chapterNumber);
        if (knowledge.isEmpty()) throw new IllegalArgumentException("No authoritative " + source + " context is available for the selected subject, target levels and NCERT scope.");
        String knowledgeText = knowledge.stream().map(Document::getText).collect(Collectors.joining("\n\n---\n\n"));
        List<String> citations = knowledge.stream().map(ncertRetrieval::citation).distinct().limit(8).toList();
        List<Document> pyqs = retrievePyqs(request);
        String pyqText = pyqs.stream().map(Document::getText).collect(Collectors.joining("\n--- PYQ ---\n"));
        Map<String, Object> constraints = profileService.toGenerationConstraints(profile);
        List<GeneratedQuestion> accepted = new ArrayList<>(); Set<String> normalizedAccepted = new HashSet<>();
        for (int attempt = 0; attempt < 4 && accepted.size() < request.count(); attempt++) {
            int remaining = request.count() - accepted.size();
            String response = chatClient.prompt().user(buildPrompt(request, constraints, pyqText, knowledgeText, remaining, source, citations)).call().content();
            for (GeneratedQuestion q : parse(response)) {
                if (accepted.size() >= request.count() || q.questionText() == null || q.questionText().isBlank()) break;
                String normalized = normalizeQuestion(q.questionText());
                if (!normalizedAccepted.add(normalized)) continue;
                if (similarityGuard.isTooSimilarToPyq(q.questionText(), request.examId())) continue;
                if (accepted.stream().anyMatch(existing -> lexicalSimilarity(existing.questionText(), q.questionText()) >= 0.86)) continue;
                accepted.add(withCitations(q, citations));
            }
        }
        if (accepted.size() < request.count()) throw new IllegalStateException("Exam-aware validation rejected too many generated questions; regenerate the section.");
        return accepted;
    }

    private List<Document> retrieveKnowledge(QuestionGenerationRequest request, String source, String corpusVersion, String bookCode, Integer chapterNumber) {
        String normalized = source.trim().toUpperCase();
        String query = request.subject() + " " + firstNonBlank(request.topic(), "fundamental concepts", null) + " " + request.questionType().name() + " principles laws formulas examples";
        if ("NCERT".equals(normalized)) return ncertRetrieval.retrieve(request.subject(), request.targetLevels(), query, corpusVersion, bookCode, chapterNumber, Math.max(12, request.count() * 5));
        List<Document> userDocs = retrieveUserDocuments(request, query);
        if ("MIXED".equals(normalized)) { List<Document> ncertDocs = ncertRetrieval.retrieve(request.subject(), request.targetLevels(), query, corpusVersion, bookCode, chapterNumber, Math.max(8, request.count() * 3)); List<Document> combined = new ArrayList<>(ncertDocs); combined.addAll(userDocs.stream().limit(Math.max(4, request.count() * 2)).toList()); return combined; }
        return userDocs;
    }

    private List<Document> retrieveUserDocuments(QuestionGenerationRequest request, String query) {
        List<String> files = metadataHelper.getDistinctFilesForSubjectAndLevels(request.subject(), request.targetLevels()); List<Document> result = new ArrayList<>();
        if (!files.isEmpty()) { int perFile = Math.max(3, request.count() * 4 / files.size()); for (String file : files) { String filter = "sourceType != 'PREVIOUS_YEAR_PAPER' && subject == '" + escape(request.subject().trim().toLowerCase()) + "' && fileName == '" + escape(file) + "'"; result.addAll(vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(perFile).similarityThreshold(0.30).filterExpression(filter).build())); } }
        return result;
    }

    private List<Document> retrievePyqs(QuestionGenerationRequest request) {
        String filter = "sourceType == 'PREVIOUS_YEAR_PAPER' && examId == '" + escape(request.examId().trim().toUpperCase()) + "'";
        return vectorStore.similaritySearch(SearchRequest.builder().query(request.subject() + " " + request.questionType().name() + " examination").topK(Math.min(10, Math.max(3, request.count()))).similarityThreshold(0.20).filterExpression(filter).build());
    }

    private String buildPrompt(QuestionGenerationRequest request, Map<String, Object> constraints, String pyqText, String knowledgeText, int count, String source, List<String> citations) {
        return """
                You are an expert examination question setter.
                Generate exactly %d NEW questions.
                The exam profile and retrieval policy are authoritative.
                Knowledge source policy: %s
                Exam profile constraints: %s
                Subject: %s
                Target levels: %s
                Requested topic: %s
                Requested type: %s
                Requested difficulty: %s
                Marks: %d

                Previous-year questions are PATTERN EVIDENCE ONLY. Never copy, paraphrase, or reproduce them.
                %s

                AUTHORITATIVE KNOWLEDGE CONTEXT:
                %s

                RETRIEVAL SOURCES (for grounding; do not invent additional sources):
                %s

                Generate questions only from the authoritative knowledge context. Do not use PYQs as factual knowledge.
                For MCQ use exactly four options and exactly one correct answer.
                Return raw JSON only: {"questions":[{"questionText":"...","questionType":"...","options":[],"correctAnswer":"...","explanation":"...","difficulty":"...","marks":%d,"topic":"..."}]}
                """.formatted(count, source, constraints, request.subject(), request.targetLevels(), request.topic() == null ? "" : request.topic(), request.questionType().name(), request.difficulty(), request.marks(), pyqText.isBlank() ? "No PYQs available." : pyqText, knowledgeText, citations.isEmpty() ? "None" : String.join("\n", citations), request.marks());
    }

    private List<GeneratedQuestion> parse(String response) { if (response == null || response.isBlank()) return List.of(); String json = response.replace("```json", "").replace("```", "").trim(); try { if (json.startsWith("{")) { ExamPaperResponse wrapper = mapper.readValue(json, ExamPaperResponse.class); return wrapper.questions() == null ? List.of() : wrapper.questions(); } return mapper.readValue(json, new TypeReference<List<GeneratedQuestion>>() {}); } catch (Exception e) { throw new IllegalStateException("Unable to parse exam-aware question response: " + e.getMessage(), e); } }
    private GeneratedQuestion withCitations(GeneratedQuestion q, List<String> citations) { return new GeneratedQuestion(q.questionText(), q.questionType(), q.options(), q.correctAnswer(), q.explanation(), q.difficulty(), q.marks(), q.topic(), citations); }
    private double lexicalSimilarity(String a, String b) { Set<String> left = java.util.Arrays.stream(normalizeQuestion(a).split(" ")).filter(s -> !s.isBlank()).collect(Collectors.toSet()); Set<String> right = java.util.Arrays.stream(normalizeQuestion(b).split(" ")).filter(s -> !s.isBlank()).collect(Collectors.toSet()); if (left.isEmpty() || right.isEmpty()) return 0; long intersection = left.stream().filter(right::contains).count(); return intersection / (double)(left.size()+right.size()-intersection); }
    private String normalizeQuestion(String value) { return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim(); }
    private String firstNonBlank(String first, String second, String fallback) { if (first != null && !first.isBlank()) return first.trim(); if (second != null && !second.isBlank()) return second.trim(); return fallback; }
    private String escape(String value) { return value == null ? "" : value.replace("'", "\\'"); }
}
