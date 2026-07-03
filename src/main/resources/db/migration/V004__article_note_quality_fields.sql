ALTER TABLE article_notes ADD COLUMN context_summary TEXT NOT NULL DEFAULT '';
ALTER TABLE article_notes ADD COLUMN why_it_matters TEXT NOT NULL DEFAULT '';
ALTER TABLE article_notes ADD COLUMN key_facts TEXT NOT NULL DEFAULT '[]';
ALTER TABLE article_notes ADD COLUMN transient_update TEXT NOT NULL DEFAULT '';
ALTER TABLE article_notes ADD COLUMN source_assessment TEXT NOT NULL DEFAULT '';
