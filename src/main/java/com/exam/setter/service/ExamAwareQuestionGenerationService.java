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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExamAwareQuestionGenerationService {
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final VectorMetadataHelperService metadataHelper;
    private final ExamProfileService profileService;
    private final QuestionSimilarityGuardService similarityGuard;
    private final ObjectMapper mapper;

    public ExamAwareQuestionGenerationService(ChatClient.Builder builder, VectorStore vectorStore,
                                              VectorMetadataHelperService metadataHelper,
                                              ExamProfileService profileService,
                                              QuestionSimilarityGuardService similarityGuard,
                                              ObjectMapper mapper) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
        this.metadataHelper = metadataHelper;
        this.profileService = profileService;
        this.similarityGuard = similarityGuard;
        this.mapper = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public List<GeneratedQuestion> generate(QuestionGenerationRequest request) {
        ExamProfileEntity profile = profileService.get(request.examId());
        List<Document> knowledge = retrieveKnowledge(request);
        if (knowledge.isEmpty()) throw new IllegalArgumentException("No knowledge context is available for the selected subject and target levels.");
        String knowledgeText = knowledge.stream().map(Document::getText).collect(Collectors.joining("\n\n---\n\n"));
        List<Document> pyqs = retrievePyqs(request);
        String pyqText = pyqs.stream().map(Document::getText).collect(Collectors.joining("\n--- PYQ ---\n"));

        Map<String, Object> constraints = profileService.toGenerationConstraints(profile);
        List<GeneratedQuestion> accepted = new ArrayList<>();
        for (int attempt = 0; attempt < 3 && accepted.size() < request.count(); attempt++) {
            int remaining = request.count() - accepted.size();
            String prompt = buildPrompt(request, constraints, pyqText, knowledgeText, remaining);
            String response = chatClient.prompt().user(prompt).call().content();
            for (GeneratedQuestion q : parse(response)) {
                if (accepted.size() >= request.count()) break;
                if (!similarityGuard.isTooSimilarToPyq(q.questionText(), request.examId())) accepted.add(q);
            }
        }
        if (accepted.size() < request.count()) {
            throw new IllegalStateException("Exam-aware validation rejected too many generated questions; regenerate the section.");
        }
        return accepted;
    }

    private List<Document> retrieveKnowledge(QuestionGenerationRequest request) {
        List<String> files = metadataHelper.getDistinctFilesForSubjectAndLevels(request.subject(), request.targetLevels());
        List<Document> result = new ArrayList<>();
        if (!files.isEmpty()) {
            int perFile = Math.max(3, request.count() * 4 / files.size());
            for (String file : files) {
                String filter = "subject == '" + escape(request.subject().trim().toLowerCase()) + "' && fileName == '" + escape(file) + "'";
                result.addAll(vectorStore.similaritySearch(SearchRequest.builder()
                        .query(request.subject() + " concepts principles laws formulas")
                        .topK(perFile).similarityThreshold(0.35).filterExpression(filter).build()));
            }
        }
        return result;
    }

    private List<Document> retrievePyqs(QuestionGenerationRequest request) {
        String filter = "sourceType == 'PREVIOUS_YEAR_PAPER' && examId == '" + escape(request.examId().trim().toUpperCase()) + "'";
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(request.subject() + " " + request.questionType().name() + " examination")
                .topK(Math.min(8, Math.max(3, request.count())))
                .similarityThreshold(0.20).filterExpression(filter).build());
    }

    private String buildPrompt(QuestionGenerationRequest request, Map<String, Object> constraints,
                               String pyqText, String knowledgeText, int count) {
        return """
                You are an expert examination question setter.
                Generate exactly %d NEW questions.
                Exam profile constraints are authoritative: %s
                Subject: %s
                Target levels: %s
                Requested type: %s
                Requested difficulty: %s
                Marks: %d

                Previous-year questions below are PATTERN EVIDENCE ONLY. Never copy, paraphrase, or reproduce them.
                %s

                The following is the authoritative knowledge corpus. Do not invent facts outside it.
                %s

                Return raw JSON only: {"questions":[{"questionText":"...","questionType":"...","options":[],"correctAnswer":"...","explanation":"...","difficulty":"...","marks":%d,"topic":"..."}]}
                """.formatted(count, constraints, request.subject(), request.targetLevels(), request.questionType().name(),
                request.difficulty(), request.marks(), pyqText.isBlank() ? "No PYQs available." : pyqText,
                knowledgeText, request.marks());
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

    private String escape(String value) { return value == null ? "" : value.replace("'", "\\'"); }
}
