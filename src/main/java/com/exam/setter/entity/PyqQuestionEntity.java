package com.exam.setter.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pyq_questions", indexes = {
        @Index(name = "idx_pyq_exam_year", columnList = "examId,year"),
        @Index(name = "idx_pyq_exam_subject", columnList = "examId,subject")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PyqQuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String examId;

    @Column(nullable = false)
    private int year;

    private Integer questionNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(columnDefinition = "TEXT")
    private String optionsJson;

    @Column(length = 50)
    private String correctAnswer;

    @Column(length = 200)
    private String subject;

    @Column(length = 200)
    private String topic;

    @Column(length = 100)
    private String questionType;

    @Column(length = 50)
    private String difficulty;

    private Integer marks;

    @Column(nullable = false)
    @Builder.Default
    private boolean visualRequired = false;

    @Column(length = 40)
    @Builder.Default
    private String visualType = "NONE";

    @Column(columnDefinition = "TEXT")
    private String visualDescription;

    @Column(length = 500)
    private String paperName;

    @Column(length = 500)
    private String sourceFileName;

    @Column(nullable = false)
    private Instant createdAt;
}
