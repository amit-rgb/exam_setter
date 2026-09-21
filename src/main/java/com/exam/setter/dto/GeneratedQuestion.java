package com.exam.setter.dto;

import com.exam.setter.model.QuestionType;
import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record GeneratedQuestion(
        @JsonAlias({"question", "text", "prompt"}) String questionText,
        @JsonAlias({"type"}) QuestionType questionType,
        @JsonAlias({"choices"}) List<String> options,
        @JsonAlias({"answer", "correct"}) String correctAnswer,
        @JsonAlias({"solution", "rationale"}) String explanation,
        @JsonAlias({"level"}) String difficulty,
        @JsonAlias({"score"}) int marks,
        @JsonAlias({"chapter", "concept"}) String topic,
        @JsonAlias({"citations"}) List<String> sourceCitations,
        @JsonAlias({"hasVisual", "visual"}) boolean visualRequired,
        @JsonAlias({"figureType", "visualKind"}) String visualType,
        @JsonAlias({"figureDescription", "visualPrompt"}) String visualDescription
) {
    public GeneratedQuestion(String questionText, QuestionType questionType, List<String> options,
                             String correctAnswer, String explanation, String difficulty, int marks, String topic) {
        this(questionText, questionType, options, correctAnswer, explanation, difficulty, marks, topic,
                List.of(), false, "NONE", "");
    }

    public GeneratedQuestion(String questionText, QuestionType questionType, List<String> options,
                             String correctAnswer, String explanation, String difficulty, int marks, String topic,
                             List<String> sourceCitations) {
        this(questionText, questionType, options, correctAnswer, explanation, difficulty, marks, topic,
                sourceCitations, false, "NONE", "");
    }
}
