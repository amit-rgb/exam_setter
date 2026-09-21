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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
public class ExamAwareQuestionGenerationService {
    private static final Logger log = LoggerFactory.getLogger(ExamAwareQuestionGenerationService.class);
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
    private final GenerationDiagnosticService diagnosticService;

    public ExamAwareQuestionGenerationService(ChatClient.Builder builder,
                                              VectorStore vectorStore,
                                              NcertRetrievalService ncertRetrieval,
                                              UploadedSourceRetrievalService uploadedRetrieval,
                                              ExamProfileService profileService,
                                              QuestionSimilarityGuardService similarityGuard,
                                              ObjectMapper mapper,
                                              GenerationDiagnosticService diagnosticService) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
        this.ncertRetrieval = ncertRetrieval;
        this.uploadedRetrieval = uploadedRetrieval;
        this.profileService = profileService;
        this.similarityGuard = similarityGuard;
        this.mapper = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.diagnosticService = diagnosticService;
    }

    public List<GeneratedQuestion> generate(QuestionGenerationRequest request) {
        ExamProfileEntity profile = request.examId() == null || request.examId().isBlank()
                ? null : profileService.get(request.examId());

        List<String> sources = resolveSources(request, profile);
        log.info("Question generation started: subject={}, levels={}, sources={}, count={}, type={}, difficulty={}, examId={}", request.subject(), request.targetLevels(), sources, request.count(), request.questionType(), request.difficulty(), request.examId());
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

        log.info("Retrieval counts: syllabus={}, teacherNotes={}, pyqs={}, other={}, ncert={}", syllabus.size(), teacherNotes.size(), pyqs.size(), other.size(), ncert.size());
        if (generationContext.isEmpty()) {
            IllegalArgumentException error = new IllegalArgumentException("No indexed material was found for the selected knowledge source(s), subject and target level. Ingest the selected source type first.");
            diagnosticService.recordFailure(Map.of("subject", request.subject(), "targetLevels", request.targetLevels(), "selectedSources", sources, "retrievedSyllabus", syllabus.size(), "retrievedTeacherNotes", teacherNotes.size(), "retrievedPyq", pyqs.size(), "retrievedOther", other.size(), "retrievedNcert", ncert.size()), error);
            log.error("No generation context found: subject={}, levels={}, sources={}", request.subject(), request.targetLevels(), sources, error);
            throw error;
        }

        String teacherText = join(teacherNotes, "\n--- TEACHER NOTE ---\n");
        String otherText = join(other, "\n--- OTHER REFERENCE ---\n");
        String syllabusText = join(syllabus, "\n--- SYLLABUS ---\n");
        String pyqText = join(pyqs, "\n--- PREVIOUS YEAR QUESTION ---\n");
        String ncertText = join(ncert, "\n--- NCERT ---\n");
        String pyqPattern = buildPyqPattern(pyqs, request.questionType());

        List<String> citations = generationContext.stream().map(this::citation).distinct().limit(16).toList();
        Map<String, Object> constraints = profile == null ? Map.of() : profileService.toGenerationConstraints(profile);

        List<GeneratedQuestion> accepted = new ArrayList<>();
        Set<String> normalizedAccepted = new HashSet<>();

        for (int attempt = 0; attempt < 6 && accepted.size() < request.count(); attempt++) {
            int remaining = request.count() - accepted.size();
            int requestedThisAttempt = Math.max(remaining, Math.min(remaining * 2, request.count() + 4));
            String response = chatClient.prompt().user(
                    buildPrompt(request, constraints, sources, pyqText, teacherText, otherText, syllabusText, ncertText, pyqPattern,
                            requestedThisAttempt, citations, attempt + 1,
                            accepted.stream().map(GeneratedQuestion::questionText).toList())
            ).call().content();

            List<GeneratedQuestion> parsedQuestions = parse(response);
            long validTextQuestions = parsedQuestions.stream()
                    .filter(q -> q.questionText() != null && !q.questionText().isBlank())
                    .count();
            log.info("Generation attempt {}: requested={}, parsed={}, validQuestionText={}, acceptedBefore={}",
                    attempt + 1, requestedThisAttempt, parsedQuestions.size(), validTextQuestions, accepted.size());
            if (validTextQuestions == 0 && !parsedQuestions.isEmpty()) {
                log.warn("LLM returned {} question objects but none contained questionText/question; response={}",
                        parsedQuestions.size(), response);
            }

            for (GeneratedQuestion q : parsedQuestions) {
                if (accepted.size() >= request.count() || q.questionText() == null || q.questionText().isBlank()) break;
                String normalized = normalizeQuestion(q.questionText());
                if (!normalizedAccepted.add(normalized)) {
                    log.debug("Rejected duplicate generated question: {}", q.questionText());
                    continue;
                }
                if (sources.contains(PYQ) && similarityGuard.isTooSimilarToPyq(q.questionText(), request.examId())) {
                    log.debug("Rejected question as too similar to PYQ: {}", q.questionText());
                    continue;
                }
                if (accepted.stream().anyMatch(existing -> lexicalSimilarity(existing.questionText(), q.questionText()) >= 0.90)) {
                    log.debug("Rejected near-duplicate generated question: {}", q.questionText());
                    continue;
                }
                accepted.add(withCitations(q, citations));
            }
        }

        log.info("Generation finished after attempts: requested={}, accepted={}", request.count(), accepted.size());

        if (accepted.size() < request.count()) {
            throw new IllegalStateException("Unable to produce enough distinct source-grounded questions after multiple generation attempts. Add more source material, choose a broader topic, or regenerate the section.");
        }
        diagnosticService.recordSuccess(Map.of("subject", request.subject(), "targetLevels", request.targetLevels(), "selectedSources", sources, "retrievedSyllabus", syllabus.size(), "retrievedTeacherNotes", teacherNotes.size(), "retrievedPyq", pyqs.size(), "retrievedOther", other.size(), "retrievedNcert", ncert.size(), "generatedQuestions", accepted.size()));
        log.info("Question generation completed: generatedQuestions={}, sources={}", accepted.size(), sources);
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
                               String syllabusText, String ncertText, String pyqPattern, int count, List<String> citations,
                               int attempt, List<String> previouslyAccepted) {
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

                PREVIOUS-YEAR PAPER PATTERN:
                %s

                SOURCE RULES:
                - TEACHER_NOTES and OTHER are factual knowledge sources.
                - SYLLABUS defines the permitted curriculum scope.
                - When SYLLABUS is the only selected source, treat the syllabus as a SCOPE document,
                  not as a factual knowledge document. Use your subject-matter knowledge to create
                  valid questions strictly within the topics explicitly covered by the syllabus.
                  Do not require the syllabus text itself to contain the answer.
                - When other factual sources are selected, use their retrieved content for factual grounding.
                - PREVIOUS_YEAR_QUESTION_PAPER is pattern and concept evidence only. Never copy, paraphrase,
                  reproduce, or lightly modify an existing question or answer.
                - NCERT, when supplied by a backward-compatible API client, is authoritative textbook content.
                - Every question in this attempt must be materially different from the previously accepted questions.
                - The previous-year pattern is a distribution guide, not a source for copying wording.
                - If questionType is MIXED, vary formats according to the observed PYQ distribution when available.
                  Do not collapse the paper into MCQs. Include visual questions when the PYQ pattern contains them,
                  but visual questions must remain a minority unless the observed pattern supports a higher share.
                - Visual questions must contain a useful visual specification: visualRequired=true and visualType
                  (GRAPH, DIAGRAM, IMAGE, MAP, TABLE, CIRCUIT, FLOWCHART). Use visualDescription to describe exactly
                  what the student should see. Non-visual questions must use visualRequired=false and visualType=NONE.
                - For SYLLABUS-only generation, the syllabus is the boundary of the curriculum, not the
                  answer bank. Questions may use standard subject knowledge to answer concepts explicitly
                  listed in the syllabus.
                - The JSON property names MUST be exactly:
                  questionText, questionType, options, correctAnswer, explanation, difficulty, marks, topic,
                  visualRequired, visualType, visualDescription.
                - For MCQ questions, options MUST contain exactly 4 strings and correctAnswer MUST be one of A, B, C, D.
                - For ASSERTION_REASON, options should contain the standard assertion/reason alternatives when appropriate.
                - For MATCHING, options should contain the matching pairs/sets in readable text.
                - For NUMERICAL and SHORT_ANSWER, options may be an empty array.
                - For CASE_BASED and STATEMENT_BASED, preserve the case/statements in questionText and use options
                  when the format requires selectable responses.
                - For MIXED, choose the format per the previous-year pattern; do not emit questionType=MIXED
                  for an individual question.

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

                Generate questions within the selected source boundaries.
                When SYLLABUS is selected without factual sources, use it strictly as curriculum scope
                and use standard subject knowledge to formulate and answer questions within that scope.
                When multiple sources are selected, combine them: use syllabus for scope, teacher/other
                material for factual grounding, and PYQs for examination pattern and coverage.
                If a selected factual source has no material, do not silently replace it with an unselected source.
                For MCQ use exactly four options and exactly one correct answer.
                Return raw JSON only.

                RETRIEVAL SOURCES:
                %s

                GENERATION ATTEMPT:
                %d

                PREVIOUSLY ACCEPTED QUESTIONS:
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
                pyqPattern,
                citations.isEmpty() ? "None" : String.join("\n", citations),
                attempt,
                previouslyAccepted == null || previouslyAccepted.isEmpty() ? "None" : String.join("\n", previouslyAccepted));
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
        String json = response.replace("```json", "").replace("```", "").trim();
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
        return new GeneratedQuestion(q.questionText(), q.questionType(), q.options(), q.correctAnswer(),
                q.explanation(), q.difficulty(), q.marks(), q.topic(), citations,
                q.visualRequired(), q.visualType(), q.visualDescription());
    }

    private String buildPyqPattern(List<Document> pyqs, com.exam.setter.model.QuestionType requestedType) {
        if (pyqs == null || pyqs.isEmpty()) {
            return requestedType == com.exam.setter.model.QuestionType.MIXED
                    ? "No PYQ pattern was retrieved. Use a balanced mix of MCQ, ASSERTION_REASON, NUMERICAL, SHORT_ANSWER and, where academically appropriate, CASE_BASED/STATEMENT_BASED questions. Include at most one visual question for every 5 questions unless the subject naturally requires more."
                    : "No PYQ pattern was retrieved. Follow the requested section type.";
        }

        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        Map<String, Integer> visualCounts = new LinkedHashMap<>();
        int visualTotal = 0;
        for (Document d : pyqs) {
            String type = String.valueOf(d.getMetadata().getOrDefault("questionType", "OTHER")).trim().toUpperCase();
            if (type.isBlank() || "NULL".equals(type)) type = "OTHER";
            typeCounts.merge(type, 1, Integer::sum);
            boolean visual = Boolean.parseBoolean(String.valueOf(d.getMetadata().getOrDefault("visualRequired", "false")));
            if (visual) {
                visualTotal++;
                String visualType = String.valueOf(d.getMetadata().getOrDefault("visualType", "OTHER")).trim().toUpperCase();
                visualCounts.merge(visualType.isBlank() ? "OTHER" : visualType, 1, Integer::sum);
            }
        }

        String typeSummary = typeCounts.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        String visualSummary = visualCounts.isEmpty()
                ? "none detected"
                : visualCounts.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "));
        return "Observed PYQ questions=" + pyqs.size()
                + "; question types: " + typeSummary
                + "; visual questions=" + visualTotal
                + "; visual formats: " + visualSummary
                + ". Match the relative variety and coverage where possible. Never copy PYQ wording.";
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
