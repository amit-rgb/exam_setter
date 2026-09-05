package com.exam.setter.dto;

import com.exam.setter.model.QuestionType;
import java.util.List;

public record QuestionGenerationRequest(
        String subject,               // e.g. "chemistry"
        List<String> targetLevels,    // Optional: ["CLASS_11"], ["CLASS_11", "CLASS_12"], ya null/empty
        QuestionType questionType,    // MCQ, NUMERICAL, etc.
        String difficulty,            // "EASY", "MEDIUM", "HARD"
        int count,                    // Number of questions
        int marks
) {}