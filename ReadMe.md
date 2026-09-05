delete the existing data from vector database and re-upload the new data.

docker exec -it question-bank-db psql -U postgres -d exambank_db -c "TRUNCATE TABLE document_embeddings, paper_questions, question_options, exam_sections, exam_papers CASCADE;"