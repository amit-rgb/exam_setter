package com.exam.setter.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_profiles", uniqueConstraints = @UniqueConstraint(name = "uk_exam_profile_exam_id", columnNames = "examId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 200)
    private String examId;

    @Column(nullable = false, length = 200)
    private String examName;

    @Column(length = 100)
    private String subject;

    @Column(length = 200)
    private String paperName;

    @Column(length = 50)
    private String knowledgeSource;

    @Column(length = 50)
    private String corpusVersion;

    @Column(length = 100)
    private String ncertBookCode;

    private Integer ncertChapterNumber;

    @Column(length = 2000)
    private String targetLevelsJson;

    private Integer questionCount;
    private Integer marksPerQuestion;
    private Integer durationMinutes;

    @Column(length = 4000)
    private String questionTypesJson;

    @Column(columnDefinition = "TEXT")
    private String difficultyDistributionJson;

    @Column(columnDefinition = "TEXT")
    private String questionTypeDistributionJson;

    @Column(columnDefinition = "TEXT")
    private String topicDistributionJson;

    @Column(length = 4000)
    private String instructions;

    @Column(length = 100)
    private String previousYearRange;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
