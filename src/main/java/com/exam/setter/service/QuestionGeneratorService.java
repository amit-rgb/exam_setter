package com.exam.setter.service;

import com.exam.setter.dto.ExamPaperResponse;
import com.exam.setter.dto.GeneratedQuestion;
import com.exam.setter.dto.QuestionGenerationRequest;
import com.exam.setter.exception.ContextNotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.stream.Collectors;

@Service
public class QuestionGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(QuestionGeneratorService.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;
    private final VectorMetadataHelperService metadataHelperService;

    public QuestionGeneratorService(ChatClient.Builder chatClientBuilder,
                                    VectorStore vectorStore,
                                    ObjectMapper objectMapper,
                                    VectorMetadataHelperService metadataHelperService) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
        this.metadataHelperService = metadataHelperService;
    }

    public List<GeneratedQuestion> generateQuestions(QuestionGenerationRequest request) {
        // 1. Identify all distinct uploaded textbook PDFs for this subject/level
        List<String> distinctFiles = metadataHelperService.getDistinctFilesForSubjectAndLevels(
                request.subject(),
                request.targetLevels()
        );

        log.info("Found {} distinct files for subject '{}': {}", distinctFiles.size(), request.subject(), distinctFiles);

        List<Document> symmetricalDocs = new ArrayList<>();

        if (!distinctFiles.isEmpty()) {
            // Symmetrical Stratified Retrieval: Fetch top chunks equally from each file
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
            // Fallback to broad search if files metadata is not populated
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

        // Shuffle chunks so the LLM doesn't bias only towards the first file in context
        Collections.shuffle(symmetricalDocs);

        String balancedTextbookContext = symmetricalDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        // 2. Structured Prompt with Symmetrical Coverage Enforcement
        String systemPrompt = """
            You are an expert examination question setter.
            CRITICAL INSTRUCTIONS:
            1. Generate exactly <count> distinct questions EXCLUSIVELY using the provided Textbook Context.
            2. DISTRIBUTE questions evenly across the different topics and concepts present in the context. Do NOT generate multiple questions testing the exact same law or definition.
            3. Output MUST be a valid JSON object containing a "questions" array.
            4. Do NOT wrap output in markdown code blocks. Output raw JSON only.
            5. Mathematical expressions and formulas MUST use standard LaTeX syntax:
               - Inline: $H_2SO_4$, $PV = nRT$
               - Block: $$\\text{PV} = \\text{nRT}$$
            6. For MCQ types, provide exactly 4 options.
            7. Provide a rigorous step-by-step scientific solution in the explanation field.

            JSON Structure Example:
            {
              "questions": [
                {
                  "questionText": "What is the molar mass of water?",
                  "questionType": "MCQ",
                  "options": ["18 g/mol", "16 g/mol", "20 g/mol", "2 g/mol"],
                  "correctAnswer": "A",
                  "explanation": "Calculated as $2 \\times 1 + 16 = 18\\text{ g/mol}$.",
                  "difficulty": "MEDIUM",
                  "marks": 4,
                  "topic": "Mole Concept"
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
        String cleanJson = rawResponse.replaceAll("```json", "").replaceAll("```", "").trim();
        try {
            if (cleanJson.startsWith("{")) {
                ExamPaperResponse wrapped = objectMapper.readValue(cleanJson, ExamPaperResponse.class);
                return wrapped.questions() != null ? wrapped.questions() : List.of();
            }
            return objectMapper.readValue(cleanJson, new TypeReference<List<GeneratedQuestion>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize LLM question response: " + e.getMessage(), e);
        }
    }
}