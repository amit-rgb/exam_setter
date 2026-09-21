package com.exam.setter.service;

import com.exam.setter.dto.GeneratedQuestion;
import com.exam.setter.dto.QuestionGenerationRequest;
import com.exam.setter.entity.ExamProfileEntity;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds the exam-aware generation context. The existing generator remains the JSON parser. */
@Service
public class ExamAwareQuestionGeneratorService {
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ExamProfileService profileService;

    public ExamAwareQuestionGeneratorService(ChatClient.Builder builder, VectorStore vectorStore,
                                              ExamProfileService profileService) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
        this.profileService = profileService;
    }

    public String generate(QuestionGenerationRequest request, String knowledgeContext) {
        if (request.examId() == null || request.examId().isBlank()) return null;

        ExamProfileEntity profile = profileService.get(request.examId());
        Map<String, Object> constraints = profileService.toGenerationConstraints(profile);
        String pyqContext = retrievePyqExamples(request).stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n--- PYQ ---\n"));

        String system = """
                You are an expert examination question setter.
                Generate exactly the requested number of NEW questions grounded in the supplied knowledge context.
                The Exam Profile controls the exam structure, difficulty, distribution, marks and instructions.
                Previous-year questions are evidence of style and structure only. Never copy, paraphrase, or reproduce them.
                Do not invent facts. For MCQ questions use exactly four options and exactly one correct answer.
                Return raw JSON only with a top-level questions array.
                """;

        String user = """
                Subject: %s
                Target levels: %s
                Requested type: %s
                Requested difficulty: %s
                Marks: %d
                Count: %d
                Exam profile: %s

                Previous-year examples for pattern reference only:
                %s

                Authoritative knowledge context:
                %s
                """.formatted(request.subject(), request.targetLevels(), request.questionType().name(),
                request.difficulty(), request.marks(), request.count(), constraints,
                pyqContext.isBlank() ? "None available." : pyqContext, knowledgeContext);

        return chatClient.prompt().system(system).user(user).call().content();
    }

    private List<Document> retrievePyqExamples(QuestionGenerationRequest request) {
        String filter = "sourceType == 'PREVIOUS_YEAR_PAPER' && examId == '" +
                request.examId().trim().toUpperCase().replace("'", "\\'") + "'";
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(request.subject() + " " + request.questionType().name() + " examination")
                .topK(Math.min(8, Math.max(3, request.count())))
                .similarityThreshold(0.20)
                .filterExpression(filter)
                .build());
    }
}
