INSERT INTO exam_profile (
    exam_id, exam_name, paper_name, knowledge_source, corpus_version,
    target_levels_json, question_types_json, difficulty_distribution_json,
    question_type_distribution_json, topic_distribution_json, instructions,
    created_at, updated_at
)
SELECT 'NCERT-MANUAL', 'NCERT Manual Blueprint', 'NCERT Assessment', 'NCERT', '2026',
       '[]', '[]', '{}', '{}', '{}',
       'Use the Blueprint sections as the examination pattern. NCERT is the authoritative knowledge source.',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM exam_profile WHERE exam_id = 'NCERT-MANUAL');
