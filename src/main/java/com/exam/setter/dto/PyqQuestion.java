package com.exam.setter.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

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
        String sourceFileName,
        Boolean visualRequired,
        String visualType,
        String visualDescription
) {}
