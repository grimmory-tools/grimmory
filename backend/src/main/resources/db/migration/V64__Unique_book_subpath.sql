ALTER TABLE book
    DROP INDEX IF EXISTS unique_file_library;

ALTER TABLE book
    ADD CONSTRAINT unique_library_file_path UNIQUE (file_name, library_id, library_path_id, file_sub_path);

