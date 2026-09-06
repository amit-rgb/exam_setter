package com.exam.setter.dto;

import com.exam.setter.model.QuestionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SectionBlueprint(
        @NotBlank @Size(max = 100) String sectionName,
        @NotNull QuestionType questionType,
        @Min(1) @Max(50) int questionCount,
        @Min(1) @Max(100) int marksPerQuestion,
        @Min(0) @Max(100) double negativeMarks,
        @NotBlank @Size(max = 20) String difficulty
) {}