package com.exam.setter.service;

import com.exam.setter.dto.ExamPaperResponse;
import com.exam.setter.dto.GeneratedQuestion;
import com.exam.setter.dto.QuestionGenerationRequest;
import com.exam.setter.exception.ContextNotFoundException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class QuestionGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(QuestionGeneratorService.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ObjectMapper lenientMapper;
    private final VectorMetadataHelperService metadataHelperService;

    public QuestionGeneratorService(ChatClient.Builder chatClientBuilder,
                                    VectorStore vectorStore,
                                    VectorMetadataHelperService metadataHelperService) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.metadataHelperService = metadataHelperService;

        // Configure Jackson to tolerate raw LaTeX backslashes without crashing
        this.lenientMapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }

    public List<GeneratedQuestion> generateQuestions(QuestionGenerationRequest request) {
        List<String> distinctFiles = metadataHelperService.getDistinctFilesForSubjectAndLevels(
                request.subject(),
                request.targetLevels()
        );

        log.info("Found {} distinct files for subject '{}': {}", distinctFiles.size(), request.subject(), distinctFiles);

        List<Document> symmetricalDocs = new ArrayList<>();

        if (!distinctFiles.isEmpty()) {
            int chunksPerFile = Math.max(3, (request.count() * 4) / distinctFiles.size());
            String semanticQuery = request.subject().trim() + " fundamental concepts laws chemical formulas principles exercises";

            for (String fileName : distinctFiles) {
                String filter = String.format("subject == '%s' && fileName == '%s'",
                        request.subject().trim().toLowerCase(), fileName);

                List<Document> fileDocs = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(semanticQuery)
                                .topK(chunksPerFile)
                                .similarityThreshold(0.35)
                                .filterExpression(filter)
                                .build()
                );
                symmetricalDocs.addAll(fileDocs);
            }
        } else {
            symmetricalDocs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(request.subject() + " fundamental concepts formulas laws")
                            .topK(Math.max(6, request.count() * 3))
                            .similarityThreshold(0.35)
                            .filterExpression(String.format("subject == '%s'", request.subject().trim().toLowerCase()))
                            .build()
            );
        }

        if (symmetricalDocs.isEmpty()) {
            throw new ContextNotFoundException(String.format(
                    "No verified context found for subject '%s' across target levels %s.",
                    request.subject(),
                    request.targetLevels()
            ));
        }

        Collections.shuffle(symmetricalDocs);

        String balancedTextbookContext = symmetricalDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        String systemPrompt = """
            You are an expert examination question setter.
            CRITICAL INSTRUCTIONS:
            1. Generate exactly <count> distinct questions EXCLUSIVELY using the provided Textbook Context.
            2. DISTRIBUTE questions evenly across the context topics. Do NOT duplicate identical laws or definitions.
            3. Output MUST be valid JSON containing a "questions" array.
            4. Do NOT wrap output in markdown code blocks. Output raw JSON only.
            5. In all LaTeX formulas, escape backslashes so JSON parsing succeeds (e.g., write \\\\Delta, \\\\rightarrow, \\\\times).
            6. For MCQ types, provide exactly 4 options.
            7. Provide a rigorous step-by-step scientific solution in the explanation field.

            JSON Structure Example:
            {
              "questions": [
                {
                  "questionText": "What is the frequency of light having a wavelength of 400 nm?",
                  "questionType": "MCQ",
                  "options": ["7.5 x 10^14 Hz", "5.0 x 10^14 Hz", "3.0 x 10^8 Hz", "1.5 x 10^15 Hz"],
                  "correctAnswer": "A",
                  "explanation": "Calculated using $\\\\nu = c / \\\\lambda$.",
                  "difficulty": "MEDIUM",
                  "marks": 4,
                  "topic": "Atomic Structure"
                }
              ]
            }
            """;

        String userPrompt = """
            Subject: <subject>
            Question Type: <questionType>
            Difficulty: <difficulty>
            Marks per question: <marks>

            Textbook Context:
            <context>
            """;

        String finalSystemPrompt = systemPrompt.replace("<count>", String.valueOf(request.count()));
        String finalUserPrompt = userPrompt
                .replace("<subject>", request.subject())
                .replace("<questionType>", request.questionType().name())
                .replace("<difficulty>", request.difficulty())
                .replace("<marks>", String.valueOf(request.marks()))
                .replace("<context>", balancedTextbookContext);

        String rawResponse = chatClient.prompt()
                .system(finalSystemPrompt)
                .user(finalUserPrompt)
                .call()
                .content();

        return parseQuestions(rawResponse);
    }

    private List<GeneratedQuestion> parseQuestions(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) return List.of();

        // 1. Strip markdown fences if present
        String cleanJson = rawResponse.replaceAll("```json", "").replaceAll("```", "").trim();

        // 2. Normalize unescaped LaTeX backslashes (e.g. \(, \), \Delta, \times)
        cleanJson = sanitizeUnescapedBackslashes(cleanJson);

        try {
            if (cleanJson.startsWith("{")) {
                ExamPaperResponse wrapped = lenientMapper.readValue(cleanJson, ExamPaperResponse.class);
                return wrapped.questions() != null ? wrapped.questions() : List.of();
            }
            return lenientMapper.readValue(cleanJson, new TypeReference<List<GeneratedQuestion>>() {});
        } catch (Exception e) {
            log.error("Failed JSON content:\n{}", cleanJson);
            throw new RuntimeException("Failed to deserialize LLM question response: " + e.getMessage(), e);
        }
    }

    private String sanitizeUnescapedBackslashes(String json) {
        // Double-escape any backslash that is not already part of a valid JSON escape sequence (\", \\, \/, \b, \f, \n, \r, \t, \\u)
        Pattern pattern = Pattern.compile("\\\\(?!([\"\\\\/bfnrt]|u[0-9a-fA-F]{4}))");
        Matcher matcher = pattern.matcher(json);
        return matcher.replaceAll("\\\\\\\\");
    }
}