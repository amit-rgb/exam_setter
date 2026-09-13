package com.exam.setter.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Structured representation of a previous-year question.
 * Kept separate from generated QuestionEntity so PYQ analysis can evolve
 * without coupling the generation persistence model to source-paper parsing.
 */
public record PyqQuestion(
        @NotBlank String examId,
        int year,
        Integer questionNumber,
        @NotBlank String questionText,
        List<String> options,
        String correctAnswer,
        String subject,
        String topic,
        String questionType,
        String difficulty,
        @Min(0) @Max(1000) Integer marks,
        String sourceFileName
) {}
