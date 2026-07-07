DROP TRIGGER IF EXISTS wiki_pages_require_major_category_insert;
DROP TRIGGER IF EXISTS wiki_pages_require_major_category_update;

ALTER TABLE wiki_sections RENAME TO wiki_categories;
ALTER TABLE wiki_pages RENAME COLUMN section_id TO subcategory_id;

DROP INDEX IF EXISTS idx_wiki_sections_fixed_nav;
DROP INDEX IF EXISTS idx_wiki_sections_nav;
DROP INDEX IF EXISTS idx_wiki_pages_section;

CREATE INDEX IF NOT EXISTS idx_wiki_categories_fixed_nav
    ON wiki_categories(fixed, status, display_order, title);

CREATE INDEX IF NOT EXISTS idx_wiki_categories_nav
    ON wiki_categories(status, display_order, title);

CREATE INDEX IF NOT EXISTS idx_wiki_pages_subcategory
    ON wiki_pages(subcategory_id, status, importance, updated_at);

CREATE TRIGGER IF NOT EXISTS wiki_pages_require_major_category_insert
BEFORE INSERT ON wiki_pages
BEGIN
    SELECT CASE
        WHEN NEW.major_category_id IS NULL THEN
            RAISE(ABORT, 'wiki page major_category_id is required')
        WHEN NOT EXISTS (
            SELECT 1
              FROM wiki_categories
             WHERE id = NEW.major_category_id
               AND fixed = 1
               AND status = 'ACTIVE'
        ) THEN
            RAISE(ABORT, 'wiki page major_category_id must reference an active major category')
        WHEN NEW.subcategory_id IS NOT NULL
         AND NOT EXISTS (
            SELECT 1
              FROM wiki_categories
             WHERE id = NEW.subcategory_id
               AND fixed = 0
               AND status = 'ACTIVE'
        ) THEN
            RAISE(ABORT, 'wiki page subcategory_id must reference an active subcategory')
    END;
END;

CREATE TRIGGER IF NOT EXISTS wiki_pages_require_major_category_update
BEFORE UPDATE OF major_category_id, subcategory_id ON wiki_pages
BEGIN
    SELECT CASE
        WHEN NEW.major_category_id IS NULL THEN
            RAISE(ABORT, 'wiki page major_category_id is required')
        WHEN NOT EXISTS (
            SELECT 1
              FROM wiki_categories
             WHERE id = NEW.major_category_id
               AND fixed = 1
               AND status = 'ACTIVE'
        ) THEN
            RAISE(ABORT, 'wiki page major_category_id must reference an active major category')
        WHEN NEW.subcategory_id IS NOT NULL
         AND NOT EXISTS (
            SELECT 1
              FROM wiki_categories
             WHERE id = NEW.subcategory_id
               AND fixed = 0
               AND status = 'ACTIVE'
        ) THEN
            RAISE(ABORT, 'wiki page subcategory_id must reference an active subcategory')
    END;
END;
