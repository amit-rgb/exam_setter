# Assessment Studio

## Exam-aware RAG foundation

The application is evolving from textbook-only RAG into an exam-aware question generation platform.

### Corpus model

- **Knowledge corpus:** NCERT, reference books, uploaded source material and other approved study documents.
- **Exam corpus:** previous-year papers, official sample papers, syllabi, marking schemes and answer keys.
- **Exam profile:** structured constraints describing how a particular exam/paper is constructed.
- **Generation:** knowledge retrieval and exam-pattern constraints are applied together; previous-year questions are reference material, not templates to reproduce.
- **Validation:** generated questions should be checked for grounding, syllabus coverage, exam-pattern adherence and similarity to known questions before final review.

### New foundation services

- `ExamPatternProfileService` converts an explicit `ExamProfileRequest` into normalized generation constraints.
- `QuestionSimilarityGuardService` provides a reusable vector-similarity guard for detecting questions that are too close to existing subject material.
- `PyqQuestion` provides the first structured representation for previous-year questions, ready for a dedicated PYQ ingestion/analyzer pipeline.

### Next implementation increments

1. Persist `ExamProfile` definitions and expose CRUD APIs.
2. Add a dedicated PYQ ingestion pipeline that extracts one question per record and preserves year/paper/question number.
3. Add an exam-pattern analyzer that calculates question-type, difficulty and topic distributions from PYQs.
4. Extend generation so the LLM receives both knowledge retrieval and exam-pattern constraints.
5. Add post-generation validation, including PYQ similarity rejection/regeneration.
6. Add UI controls for selecting an exam profile independently from subject and target level.
