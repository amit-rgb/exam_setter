package com.exam.setter.dto;

import com.exam.setter.model.QuestionType;
import java.util.List;

public record GeneratedQuestion(
        String questionText,
        QuestionType questionType,
        List<String> options,
        String correctAnswer,
        String explanation,
        String difficulty,
        int marks,
        String topic,
        List<String> sourceCitations
) {
    public GeneratedQuestion(String questionText, QuestionType questionType, List<String> options,
                             String correctAnswer, String explanation, String difficulty, int marks, String topic) {
        this(questionText, questionType, options, correctAnswer, explanation, difficulty, marks, topic, List.of());
    }
}
