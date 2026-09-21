package com.exam.setter.service;

import com.exam.setter.dto.PyqQuestion;
import com.exam.setter.entity.PyqQuestionEntity;
import com.exam.setter.repository.PyqQuestionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PyqIngestionService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final PyqQuestionRepository repository;
    private final VectorStore vectorStore;

    public PyqIngestionService(ChatClient.Builder chatClientBuilder,
                               ObjectMapper objectMapper,
                               PyqQuestionRepository repository,
                               VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.vectorStore = vectorStore;
    }

    /**
     * Extracts structured questions from one previous-year paper. The LLM is used
     * only for document structure recovery/classification; the original question
     * text is persisted separately and the PDF itself remains the source record.
     */
    public List<PyqQuestionEntity> ingestPdf(MultipartFile file, String examId, int year,
                                             String paperName, String subject) throws IOException {
        return ingestPdf(file, examId, year, paperName, subject, null);
    }

    public List<PyqQuestionEntity> ingestPdf(MultipartFile file, String examId, int year,
                                             String paperName, String subject, String targetLevel) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("A PYQ PDF is required.");
        if (year < 1900 || year > 2100) throw new IllegalArgumentException("PYQ year is invalid.");
        if (examId == null || examId.isBlank()) throw new IllegalArgumentException("examId is required.");

        Path temp = Files.createTempFile("pyq_ingest_", ".pdf");
        file.transferTo(temp);
        String text;
        try {
            List<Document> docs = new TikaDocumentReader(new FileSystemResource(temp.toFile())).get();
            text = docs.stream().map(Document::getText).reduce("", (a, b) -> a + "\n\n" + b);
        } finally {
            Files.deleteIfExists(temp);
        }

        if (text.isBlank()) throw new IllegalArgumentException("No readable text was extracted from the PYQ PDF.");

        List<PyqQuestion> extracted = extractQuestions(text, examId, year, paperName, subject);
        if (extracted.isEmpty()) throw new IllegalArgumentException("No structured questions could be extracted from the PYQ PDF.");

        List<PyqQuestionEntity> saved = new ArrayList<>();
        List<Document> vectors = new ArrayList<>();
        for (PyqQuestion q : extracted) {
            PyqQuestionEntity entity = PyqQuestionEntity.builder()
                    .examId(examId.trim().toUpperCase())
                    .year(year)
                    .questionNumber(q.questionNumber())
                    .questionText(q.questionText())
                    .optionsJson(write(q.options()))
                    .correctAnswer(q.correctAnswer())
                    .subject(firstNonBlank(q.subject(), subject))
                    .topic(q.topic())
                    .questionType(q.questionType())
                    .difficulty(q.difficulty())
                    .marks(q.marks())
                    .visualRequired(Boolean.TRUE.equals(q.visualRequired()))
                    .visualType(q.visualType() == null ? "NONE" : q.visualType())
                    .visualDescription(q.visualDescription())
                    .paperName(paperName)
                    .sourceFileName(file.getOriginalFilename())
                    .createdAt(Instant.now())
                    .build();
            saved.add(repository.save(entity));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "PYQ");
            metadata.put("sourceType", "PREVIOUS_YEAR_PAPER");
            metadata.put("examId", examId.trim().toUpperCase());
            metadata.put("year", year);
            metadata.put("paperName", paperName == null ? "" : paperName);
            metadata.put("subject", firstNonBlank(q.subject(), subject));
            if (targetLevel != null && !targetLevel.isBlank()) metadata.put("targetLevels", targetLevel.trim().toUpperCase());
            metadata.put("topic", q.topic() == null ? "" : q.topic());
            metadata.put("questionType", q.questionType() == null ? "" : q.questionType());
            metadata.put("difficulty", q.difficulty() == null ? "" : q.difficulty());
            metadata.put("questionNumber", q.questionNumber() == null ? 0 : q.questionNumber());
            metadata.put("visualRequired", Boolean.TRUE.equals(q.visualRequired()));
            metadata.put("visualType", q.visualType() == null ? "NONE" : q.visualType());
            metadata.put("visualDescription", q.visualDescription() == null ? "" : q.visualDescription());
            metadata.put("fileName", file.getOriginalFilename());
            vectors.add(new Document(q.questionText(), metadata));
        }

        for (int i = 0; i < vectors.size(); i += 50) {
            vectorStore.add(vectors.subList(i, Math.min(i + 50, vectors.size())));
        }
        return saved;
    }

    private List<PyqQuestion> extractQuestions(String text, String examId, int year,
                                                String paperName, String subject) {
        String prompt = """
                Extract every individual question from the supplied previous-year examination paper.
                Return ONLY a JSON array. Preserve question wording and option wording exactly as far as possible.
                Do not invent an answer, topic, difficulty, or question type. Use null when the PDF does not support it.
                Classify questionType only when evident, using values such as MCQ, ASSERTION_REASON, NUMERICAL,
                MATCHING, STATEMENT_BASED, SHORT_ANSWER, ESSAY, or OTHER.
                difficulty must be EASY, MEDIUM, HARD, or null unless the paper itself supports a stronger classification.
                Each object must contain: examId, year, questionNumber, questionText, options, correctAnswer,
                subject, topic, questionType, difficulty, marks, sourceFileName.

                Exam ID: %s
                Year: %d
                Paper: %s
                Subject hint: %s

                PAPER TEXT:
                %s
                """.formatted(examId, year, paperName == null ? "" : paperName,
                subject == null ? "" : subject, text);

        String response = chatClient.prompt().user(prompt).call().content();
        if (response == null || response.isBlank()) return List.of();
        String json = response.replace("```json", "").replace("```", "").trim();
        try {
            return objectMapper.readValue(json, new TypeReference<List<PyqQuestion>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse the structured PYQ extraction response: " + e.getMessage(), e);
        }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? List.of() : value); }
        catch (Exception e) { return "[]"; }
    }

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first.trim() : fallback;
    }
}
