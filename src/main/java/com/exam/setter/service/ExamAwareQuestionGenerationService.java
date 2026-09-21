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
    private static final String SYLLABUS = "SYLLABUS";
    private static final String TEACHER_NOTES = "TEACHER_NOTES";
    private static final String PYQ = "PREVIOUS_YEAR_QUESTION_PAPER";
    private static final String OTHER = "OTHER";

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final UploadedSourceRetrievalService uploadedRetrieval;
    private final NcertRetrievalService ncertRetrieval;
    private final ExamProfileService profileService;
    private final QuestionSimilarityGuardService similarityGuard;
    private final ObjectMapper mapper;

    public ExamAwareQuestionGenerationService(ChatClient.Builder builder,
                                              VectorStore vectorStore,
                                              NcertRetrievalService ncertRetrieval,
                                              UploadedSourceRetrievalService uploadedRetrieval,
                                              ExamProfileService profileService,
                                              QuestionSimilarityGuardService similarityGuard,
                                              ObjectMapper mapper) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
        this.ncertRetrieval = ncertRetrieval;
        this.uploadedRetrieval = uploadedRetrieval;
        this.profileService = profileService;
        this.similarityGuard = similarityGuard;
        this.mapper = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public List<GeneratedQuestion> generate(QuestionGenerationRequest request) {
        ExamProfileEntity profile = request.examId() == null || request.examId().isBlank()
                ? null : profileService.get(request.examId());

        List<String> sources = resolveSources(request, profile);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("Select at least one knowledge source before generating the paper.");
        }

        String corpusVersion = firstNonBlank(request.corpusVersion(), profile == null ? null : profile.getCorpusVersion(), null);
        String bookCode = firstNonBlank(request.bookCode(), profile == null ? null : profile.getNcertBookCode(), null);
        Integer chapterNumber = request.chapterNumber() != null ? request.chapterNumber() : profile == null ? null : profile.getNcertChapterNumber();
        String query = buildQuery(request);

        List<Document> teacherNotes = sources.contains(TEACHER_NOTES)
                ? uploadedRetrieval.retrieveKnowledge(request.subject(), request.targetLevels(), query, List.of("STUDY_NOTES"), Math.max(8, request.count() * 4))
                : List.of();
        List<Document> other = sources.contains(OTHER)
                ? uploadedRetrieval.retrieveKnowledge(request.subject(), request.targetLevels(), query, List.of("REFERENCE"), Math.max(8, request.count() * 4))
                : List.of();
        List<Document> syllabus = sources.contains(SYLLABUS)
                ? uploadedRetrieval.retrieveSyllabusScope(request.subject(), request.targetLevels(), query, Math.max(4, request.count() * 2))
                : List.of();
        List<Document> pyqs = sources.contains(PYQ) ? retrievePyqs(request) : List.of();
        List<Document> ncert = sources.contains("NCERT")
                ? ncertRetrieval.retrieve(request.subject(), request.targetLevels(), query, corpusVersion, bookCode, chapterNumber, Math.max(12, request.count() * 5))
                : List.of();

        List<Document> generationContext = new ArrayList<>();
        generationContext.addAll(teacherNotes);
        generationContext.addAll(other);
        generationContext.addAll(syllabus);
        generationContext.addAll(pyqs);
        generationContext.addAll(ncert);

        if (generationContext.isEmpty()) {
            throw new IllegalArgumentException("No indexed material was found for the selected knowledge source(s), subject and target level. Ingest the selected source type first.");
        }

        String teacherText = join(teacherNotes, "\n--- TEACHER NOTE ---\n");
        String otherText = join(other, "\n--- OTHER REFERENCE ---\n");
        String syllabusText = join(syllabus, "\n--- SYLLABUS ---\n");
        String pyqText = join(pyqs, "\n--- PREVIOUS YEAR QUESTION ---\n");
        String ncertText = join(ncert, "\n--- NCERT ---\n");

        List<String> citations = generationContext.stream().map(this::citation).distinct().limit(16).toList();
        Map<String, Object> constraints = profile == null ? Map.of() : profileService.toGenerationConstraints(profile);

        List<GeneratedQuestion> accepted = new ArrayList<>();
        Set<String> normalizedAccepted = new HashSet<>();

        for (int attempt = 0; attempt < 4 && accepted.size() < request.count(); attempt++) {
            int remaining = request.count() - accepted.size();
            String response = chatClient.prompt().user(
                    buildPrompt(request, constraints, sources, pyqText, teacherText, otherText, syllabusText, ncertText, remaining, citations)
            ).call().content();

            for (GeneratedQuestion q : parse(response)) {
                if (accepted.size() >= request.count() || q.questionText() == null || q.questionText().isBlank()) break;
                String normalized = normalizeQuestion(q.questionText());
                if (!normalizedAccepted.add(normalized)) continue;
                if (sources.contains(PYQ) && similarityGuard.isTooSimilarToPyq(q.questionText(), request.examId())) continue;
                if (accepted.stream().anyMatch(existing -> lexicalSimilarity(existing.questionText(), q.questionText()) >= 0.86)) continue;
                accepted.add(withCitations(q, citations));
            }
        }

        if (accepted.size() < request.count()) {
            throw new IllegalStateException("Source-grounded validation rejected too many generated questions; broaden the selected source material or regenerate the section.");
        }
        return accepted;
    }

    private List<String> resolveSources(QuestionGenerationRequest request, ExamProfileEntity profile) {
        List<String> selected = request.knowledgeSources() == null ? List.of() : request.knowledgeSources().stream()
                .filter(v -> v != null && !v.isBlank()).map(this::normalizeSource).distinct().toList();
        if (!selected.isEmpty()) return selected;

        String legacy = firstNonBlank(request.knowledgeSource(), profile == null ? null : profile.getKnowledgeSource(), null);
        if (legacy == null) return List.of();
        if ("MIXED".equalsIgnoreCase(legacy)) return List.of("NCERT", TEACHER_NOTES, OTHER);
        if ("USER_UPLOAD".equalsIgnoreCase(legacy)) return List.of(TEACHER_NOTES, OTHER);
        return List.of(normalizeSource(legacy));
    }

    private String normalizeSource(String value) {
        String v = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (v) {
            case "STUDY_NOTES", "TEACHER_NOTE", "TEACHER_NOTES" -> TEACHER_NOTES;
            case "PREVIOUS_YEAR_PAPER", "PREVIOUS_YEAR_QUESTION_PAPER", "PYQ" -> PYQ;
            case "SYLLABUS" -> SYLLABUS;
            case "REFERENCE", "OTHER" -> OTHER;
            default -> v;
        };
    }

    private String buildQuery(QuestionGenerationRequest request) {
        return request.subject() + " " + firstNonBlank(request.topic(), "fundamental concepts", null) + " "
                + request.questionType().name() + " principles laws formulas examples";
    }

    private List<Document> retrievePyqs(QuestionGenerationRequest request) {
        String subject = request.subject() == null ? "" : request.subject().trim().toLowerCase();
        String filter = "sourceType == 'PREVIOUS_YEAR_PAPER' && subject == '" + escape(subject) + "'";
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(buildQuery(request))
                .topK(Math.min(12, Math.max(4, request.count() * 2)))
                .similarityThreshold(0.20)
                .filterExpression(filter)
                .build());
    }

    private String buildPrompt(QuestionGenerationRequest request, Map<String, Object> constraints,
                               List<String> sources, String pyqText, String teacherText, String otherText,
                               String syllabusText, String ncertText, int count, List<String> citations) {
        return """
                You are an expert examination question setter.
                Generate exactly %d NEW questions.

                SELECTED KNOWLEDGE SOURCES: %s
                Exam profile constraints: %s
                Subject: %s
                Target levels: %s
                Requested topic: %s
                Requested type: %s
                Requested difficulty: %s
                Marks: %d

                SOURCE RULES:
                - TEACHER_NOTES and OTHER are factual knowledge sources.
                - SYLLABUS defines the permitted curriculum scope. Do not invent content outside it.
                - PREVIOUS_YEAR_QUESTION_PAPER is pattern and concept evidence only. Never copy, paraphrase,
                  reproduce, or lightly modify an existing question or answer.
                - NCERT, when supplied by a backward-compatible API client, is authoritative textbook content.

                SYLLABUS:
                %s

                TEACHER NOTES:
                %s

                OTHER:
                %s

                PREVIOUS YEAR QUESTIONS:
                %s

                NCERT:
                %s

                Generate only questions that can be grounded in the selected source material.
                When multiple sources are selected, combine them: use syllabus for scope, teacher/other
                material for factual grounding, and PYQs for examination pattern and coverage.
                If a selected source has no material, do not silently replace it with an unselected source.
                For MCQ use exactly four options and exactly one correct answer.
                Return raw JSON only.

                RETRIEVAL SOURCES:
                %s
                """.formatted(
                count, sources, constraints, request.subject(), request.targetLevels(),
                request.topic() == null ? "" : request.topic(), request.questionType().name(),
                request.difficulty(), request.marks(),
                syllabusText.isBlank() ? "No syllabus material selected or indexed." : syllabusText,
                teacherText.isBlank() ? "No teacher notes selected or indexed." : teacherText,
                otherText.isBlank() ? "No other reference material selected or indexed." : otherText,
                pyqText.isBlank() ? "No previous-year questions selected or indexed." : pyqText,
                ncertText.isBlank() ? "No NCERT material selected." : ncertText,
                citations.isEmpty() ? "None" : String.join("\n", citations));
    }

    private String join(List<Document> documents, String delimiter) {
        return documents.stream().map(Document::getText).filter(text -> text != null && !text.isBlank()).collect(Collectors.joining(delimiter));
    }

    private String citation(Document document) {
        String source = String.valueOf(document.getMetadata().getOrDefault("source", ""));
        return "NCERT".equalsIgnoreCase(source) ? ncertRetrieval.citation(document) : uploadedRetrieval.citation(document);
    }

    private List<GeneratedQuestion> parse(String response) {
        if (response == null || response.isBlank()) return List.of();
        String json = response.replace("\`\`\`json", "").replace("\`\`\`", "").trim();
        try {
            if (json.startsWith("{")) {
                ExamPaperResponse wrapper = mapper.readValue(json, ExamPaperResponse.class);
                return wrapper.questions() == null ? List.of() : wrapper.questions();
            }
            return mapper.readValue(json, new TypeReference<List<GeneratedQuestion>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse exam-aware question response: " + e.getMessage(), e);
        }
    }

    private GeneratedQuestion withCitations(GeneratedQuestion q, List<String> citations) {
        return new GeneratedQuestion(q.questionText(), q.questionType(), q.options(), q.correctAnswer(), q.explanation(), q.difficulty(), q.marks(), q.topic(), citations);
    }

    private double lexicalSimilarity(String a, String b) {
        Set<String> left = java.util.Arrays.stream(normalizeQuestion(a).split(" ")).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        Set<String> right = java.util.Arrays.stream(normalizeQuestion(b).split(" ")).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        if (left.isEmpty() || right.isEmpty()) return 0;
        long intersection = left.stream().filter(right::contains).count();
        return intersection / (double) (left.size() + right.size() - intersection);
    }

    private String normalizeQuestion(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return fallback;
    }

    private String escape(String value) { return value == null ? "" : value.replace("'", "\\'"); }
}
