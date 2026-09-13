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
        @NotEmpty @Size(max = 10) List<@Valid SectionBlueprint> sections,
        @Size(max = 200) String examId,
        @Size(max = 50) String knowledgeSource,
        @Size(max = 50) String corpusVersion,
        @Size(max = 100) String ncertBookCode,
        @Min(1) @Max(1000) Integer ncertChapterNumber
) {
    public ExamPaperBlueprintRequest(String examTitle, String subject, List<String> targetLevels,
                                     int durationMinutes, List<SectionBlueprint> sections) {
        this(examTitle, subject, targetLevels, durationMinutes, sections, null, null, null, null, null);
    }
}
