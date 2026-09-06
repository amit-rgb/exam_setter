package com.exam.setter.dto;

import com.exam.setter.model.QuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QuestionGenerationRequest(
        @NotBlank @Size(max = 100) String subject,
        @Size(max = 10) List<@NotBlank @Size(max = 50) String> targetLevels,
        @NotNull QuestionType questionType,
        @NotBlank @Size(max = 20) String difficulty,
        @Min(1) @Max(50) int count,
        @Min(1) @Max(100) int marks
) {}