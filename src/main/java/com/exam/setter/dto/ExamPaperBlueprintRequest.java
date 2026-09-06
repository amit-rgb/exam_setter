package com.exam.setter.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ExamPaperBlueprintRequest(
        @NotBlank @Size(max = 200) String examTitle,
        @NotBlank @Size(max = 100) String subject,
        @Size(max = 10) List<@NotBlank @Size(max = 50) String> targetLevels,
        @Min(1) @Max(600) int durationMinutes,
        @NotEmpty @Size(max = 10) List<@Valid SectionBlueprint> sections
) {}