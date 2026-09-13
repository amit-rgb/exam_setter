package com.exam.setter.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record ExamProfileRequest(
        @NotBlank @Size(max = 200) String examId,
        @NotBlank @Size(max = 200) String examName,
        @Size(max = 200) String paperName,
        @Size(max = 10) List<@NotBlank @Size(max = 50) String> targetLevels,
        @Min(1) @Max(1000) Integer questionCount,
        Integer marksPerQuestion,
        Integer durationMinutes,
        @Size(max = 100) List<String> questionTypes,
        Map<String, Double> difficultyDistribution,
        Map<String, Double> questionTypeDistribution,
        Map<String, Double> topicDistribution,
        @Size(max = 2000) String instructions,
        String previousYearRange
) {}