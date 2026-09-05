package com.exam.setter.dto;

import com.exam.setter.model.QuestionType;
import java.util.List;

public record GeneratedQuestion(
        String questionText,          // Contains raw LaTeX enclosed in $ or $$
        QuestionType questionType,    // MCQ, NUMERICAL, etc.
        List<String> options,         // Size 4 for MCQ, empty for numerical/descriptive
        String correctAnswer,         // "A", "B", "C", "D" or exact numeric/short string
        String explanation,           // Step-by-step scientific solution with LaTeX formulas
        String difficulty,            // "EASY", "MEDIUM", "HARD"
        int marks,
        String topic
) {}