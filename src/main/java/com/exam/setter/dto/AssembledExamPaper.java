package com.exam.setter.dto;

import java.time.Instant;
import java.util.List;

public record AssembledExamPaper(
        String examTitle,
        String subject,
        List<String> targetLevels,
        int durationMinutes,
        int totalMarks,
        int totalQuestions,
        List<AssembledSection> sections,
        Instant generatedAt
) {
    public record AssembledSection(
            String sectionName,
            int totalSectionMarks,
            double negativeMarksPerQuestion,
            List<GeneratedQuestion> questions
    ) {}
}