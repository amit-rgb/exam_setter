package com.exam.setter.entity;

import com.exam.setter.model.PaperStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "exam_papers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamPaperEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String subject;

    private int durationMinutes;
    private int totalMarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaperStatus status;

    private Instant createdAt;

    @OneToMany(mappedBy = "examPaper", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ExamSectionEntity> sections = new ArrayList<>();
}