-- Add the expert's print-selection flag safely for existing PostgreSQL/Docker databases.
-- Existing questions are intentionally excluded until an expert opts them in.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'paper_questions'
    ) THEN
        ALTER TABLE paper_questions
            ADD COLUMN IF NOT EXISTS included_in_paper BOOLEAN DEFAULT FALSE;

        UPDATE paper_questions
        SET included_in_paper = FALSE
        WHERE included_in_paper IS NULL;

        ALTER TABLE paper_questions
            ALTER COLUMN included_in_paper SET DEFAULT FALSE;

        ALTER TABLE paper_questions
            ALTER COLUMN included_in_paper SET NOT NULL;
    END IF;
END $$;
