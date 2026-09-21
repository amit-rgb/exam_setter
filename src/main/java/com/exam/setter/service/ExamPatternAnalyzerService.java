package com.exam.setter.service;

import com.exam.setter.entity.PyqQuestionEntity;
import com.exam.setter.repository.PyqQuestionRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExamPatternAnalyzerService {

    private final PyqQuestionRepository repository;

    public ExamPatternAnalyzerService(PyqQuestionRepository repository) {
        this.repository = repository;
    }

    /**
     * Calculates empirical distributions from the structured PYQ corpus.
     * Missing classifications are reported separately instead of guessed.
     */
    public Map<String, Object> analyze(String examId) {
        List<PyqQuestionEntity> questions = repository.findByExamIdIgnoreCaseOrderByYearAscQuestionNumberAsc(examId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("examId", examId);
        result.put("questionCount", questions.size());
        result.put("years", questions.stream().map(PyqQuestionEntity::getYear).distinct().sorted().toList());
        result.put("questionTypeDistribution", distribution(questions, PyqQuestionEntity::getQuestionType));
        result.put("difficultyDistribution", distribution(questions, PyqQuestionEntity::getDifficulty));
        result.put("subjectDistribution", distribution(questions, PyqQuestionEntity::getSubject));
        result.put("topicDistribution", distribution(questions, PyqQuestionEntity::getTopic));
        result.put("classifiedQuestionTypes", questions.stream().filter(q -> hasText(q.getQuestionType())).count());
        result.put("classifiedDifficulties", questions.stream().filter(q -> hasText(q.getDifficulty())).count());
        result.put("classifiedTopics", questions.stream().filter(q -> hasText(q.getTopic())).count());
        return result;
    }

    private Map<String, Double> distribution(List<PyqQuestionEntity> questions,
                                              Function<PyqQuestionEntity, String> classifier) {
        Map<String, Long> counts = questions.stream()
                .map(classifier)
                .filter(this::hasText)
                .map(value -> value.trim().toUpperCase())
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) return Map.of();
        return counts.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> Math.round((entry.getValue() * 10000.0 / total)) / 100.0,
                (a, b) -> a,
                LinkedHashMap::new
        ));
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
