ALTER TABLE library_path
    DROP FOREIGN KEY IF EXISTS fk_library_path;
ALTER TABLE library_path
    ADD CONSTRAINT fk_library_path FOREIGN KEY (library_id) REFERENCES library (id) ON DELETE CASCADE;
