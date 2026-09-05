package com.exam.setter.service;

import com.exam.setter.dto.ExamPaperResponse;
import com.exam.setter.dto.GeneratedQuestion;
import com.exam.setter.dto.QuestionGenerationRequest;
import com.exam.setter.exception.ContextNotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class QuestionGeneratorService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public QuestionGeneratorService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    public List<GeneratedQuestion> generateQuestions(QuestionGenerationRequest request) {
        String semanticQuery = request.subject().trim() + " important laws concepts chemical formulas definitions questions";

        String filterExpression = buildFilterExpression(request.subject(), request.targetLevels());

        SearchRequest searchRequest = SearchRequest.builder()
                .query(semanticQuery)
                .topK(Math.max(4, request.count() * 3))
                .similarityThreshold(0.40)
                .filterExpression(filterExpression)
                .build();

        List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);

        if (relevantDocs == null || relevantDocs.isEmpty()) {
            throw new ContextNotFoundException(String.format(
                    "No verified context found for subject '%s' with levels: %s. Please ingest textbooks first.",
                    request.subject(),
                    request.targetLevels() == null ? "ALL" : request.targetLevels()
            ));
        }

        String textbookContext = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        String systemPrompt = """
            You are an expert examination question setter.
            CRITICAL INSTRUCTIONS:
            1. Generate exactly <count> distinct questions EXCLUSIVELY using the provided Textbook Context.
            2. Output MUST be a single valid JSON object containing a "questions" array.
            3. Do NOT wrap output in markdown ticks (no ```json). Output raw JSON only.
            4. All mathematical expressions and chemical formulas MUST use standard LaTeX syntax:
               - Inline: $H_2SO_4$, $PV = nRT$
               - Block: $$\\text{PV} = \\text{nRT}$$
            5. For MCQ types, provide exactly 4 options.
            6. Provide a rigorous step-by-step scientific solution in the explanation field.

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
                .replace("<context>", textbookContext);

        String rawResponse = chatClient.prompt()
                .system(finalSystemPrompt)
                .user(finalUserPrompt)
                .call()
                .content();

        return parseQuestions(rawResponse);
    }

    private List<GeneratedQuestion> parseQuestions(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return List.of();
        }

        // Markdown code fence cleaning
        String cleanJson = rawResponse.replaceAll("```json", "").replaceAll("```", "").trim();

        try {
            // Case 1: Wrapper Object format {"questions": [...]}
            if (cleanJson.startsWith("{")) {
                ExamPaperResponse wrapped = objectMapper.readValue(cleanJson, ExamPaperResponse.class);
                return wrapped.questions() != null ? wrapped.questions() : List.of();
            }
            // Case 2: Direct Array format [...]
            return objectMapper.readValue(cleanJson, new TypeReference<List<GeneratedQuestion>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize LLM question response: " + e.getMessage(), e);
        }
    }

    private String buildFilterExpression(String subject, List<String> targetLevels) {
        String baseFilter = String.format("subject == '%s'", subject.trim().toLowerCase());

        if (targetLevels == null || targetLevels.isEmpty()) {
            return baseFilter;
        }

        if (targetLevels.size() == 1) {
            return String.format("%s && targetLevel == '%s'", baseFilter, targetLevels.get(0).trim().toUpperCase());
        }

        String levelFilters = targetLevels.stream()
                .map(lvl -> String.format("targetLevel == '%s'", lvl.trim().toUpperCase()))
                .collect(Collectors.joining(" || "));

        return String.format("%s && (%s)", baseFilter, levelFilters);
    }
}