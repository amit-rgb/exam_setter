package com.exam.setter.service;

import com.exam.setter.dto.ExamProfileRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves an explicit exam profile into compact generation constraints.
 *
 * This first foundation deliberately keeps the profile in memory rather than
 * introducing new persistence tables. The next increment can persist profiles
 * and derive them automatically from structured previous-year questions.
 */
@Service
public class ExamPatternProfileService {

    public Map<String, Object> buildGenerationConstraints(ExamProfileRequest profile) {
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("examId", profile.examId());
        constraints.put("examName", profile.examName());
        constraints.put("paperName", profile.paperName());
        constraints.put("questionCount", profile.questionCount());
        constraints.put("marksPerQuestion", profile.marksPerQuestion());
        constraints.put("durationMinutes", profile.durationMinutes());
        constraints.put("targetLevels", safeList(profile.targetLevels()));
        constraints.put("questionTypes", safeList(profile.questionTypes()));
        constraints.put("difficultyDistribution", profile.difficultyDistribution());
        constraints.put("questionTypeDistribution", profile.questionTypeDistribution());
        constraints.put("topicDistribution", profile.topicDistribution());
        constraints.put("previousYearRange", profile.previousYearRange());
        constraints.put("instructions", profile.instructions());
        return constraints;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
