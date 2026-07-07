ALTER TABLE wiki_pages ADD COLUMN major_category_id INTEGER;

UPDATE wiki_pages
   SET major_category_id = section_id
 WHERE section_id IN (
       SELECT id
         FROM wiki_sections
        WHERE fixed = 1
 );

UPDATE wiki_pages
   SET major_category_id = (
       SELECT id
         FROM wiki_sections
        WHERE slug = 'other'
        LIMIT 1
   )
 WHERE major_category_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_wiki_pages_major_category
    ON wiki_pages(major_category_id, status, importance, updated_at);

CREATE TRIGGER IF NOT EXISTS wiki_pages_require_major_category_insert
BEFORE INSERT ON wiki_pages
BEGIN
    SELECT CASE
        WHEN NEW.major_category_id IS NULL THEN
            RAISE(ABORT, 'wiki page major_category_id is required')
        WHEN NOT EXISTS (
            SELECT 1
              FROM wiki_sections
             WHERE id = NEW.major_category_id
               AND fixed = 1
               AND status = 'ACTIVE'
        ) THEN
            RAISE(ABORT, 'wiki page major_category_id must reference an active major category')
    END;
END;

CREATE TRIGGER IF NOT EXISTS wiki_pages_require_major_category_update
BEFORE UPDATE OF major_category_id ON wiki_pages
BEGIN
    SELECT CASE
        WHEN NEW.major_category_id IS NULL THEN
            RAISE(ABORT, 'wiki page major_category_id is required')
        WHEN NOT EXISTS (
            SELECT 1
              FROM wiki_sections
             WHERE id = NEW.major_category_id
               AND fixed = 1
               AND status = 'ACTIVE'
        ) THEN
            RAISE(ABORT, 'wiki page major_category_id must reference an active major category')
    END;
END;
