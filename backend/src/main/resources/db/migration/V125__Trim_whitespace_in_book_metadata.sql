-- Trim leading/trailing whitespace from string columns in book_metadata.
-- Blank-after-trim values are set to NULL to avoid empty-string duplicates.

UPDATE book_metadata SET title = NULLIF(TRIM(title), '') WHERE title IS NOT NULL AND title != TRIM(title);
UPDATE book_metadata SET subtitle = NULLIF(TRIM(subtitle), '') WHERE subtitle IS NOT NULL AND subtitle != TRIM(subtitle);
UPDATE book_metadata SET publisher = NULLIF(TRIM(publisher), '') WHERE publisher IS NOT NULL AND publisher != TRIM(publisher);
UPDATE book_metadata SET series_name = NULLIF(TRIM(series_name), '') WHERE series_name IS NOT NULL AND series_name != TRIM(series_name);
UPDATE book_metadata SET language = NULLIF(TRIM(language), '') WHERE language IS NOT NULL AND language != TRIM(language);
UPDATE book_metadata SET isbn_13 = NULLIF(TRIM(isbn_13), '') WHERE isbn_13 IS NOT NULL AND isbn_13 != TRIM(isbn_13);
UPDATE book_metadata SET isbn_10 = NULLIF(TRIM(isbn_10), '') WHERE isbn_10 IS NOT NULL AND isbn_10 != TRIM(isbn_10);
UPDATE book_metadata SET asin = NULLIF(TRIM(asin), '') WHERE asin IS NOT NULL AND asin != TRIM(asin);
UPDATE book_metadata SET goodreads_id = NULLIF(TRIM(goodreads_id), '') WHERE goodreads_id IS NOT NULL AND goodreads_id != TRIM(goodreads_id);
UPDATE book_metadata SET hardcover_id = NULLIF(TRIM(hardcover_id), '') WHERE hardcover_id IS NOT NULL AND hardcover_id != TRIM(hardcover_id);
UPDATE book_metadata SET hardcover_book_id = NULLIF(TRIM(hardcover_book_id), '') WHERE hardcover_book_id IS NOT NULL AND hardcover_book_id != TRIM(hardcover_book_id);
UPDATE book_metadata SET google_id = NULLIF(TRIM(google_id), '') WHERE google_id IS NOT NULL AND google_id != TRIM(google_id);
UPDATE book_metadata SET comicvine_id = NULLIF(TRIM(comicvine_id), '') WHERE comicvine_id IS NOT NULL AND comicvine_id != TRIM(comicvine_id);
UPDATE book_metadata SET lubimyczytac_id = NULLIF(TRIM(lubimyczytac_id), '') WHERE lubimyczytac_id IS NOT NULL AND lubimyczytac_id != TRIM(lubimyczytac_id);
UPDATE book_metadata SET ranobedb_id = NULLIF(TRIM(ranobedb_id), '') WHERE ranobedb_id IS NOT NULL AND ranobedb_id != TRIM(ranobedb_id);
UPDATE book_metadata SET audible_id = NULLIF(TRIM(audible_id), '') WHERE audible_id IS NOT NULL AND audible_id != TRIM(audible_id);
UPDATE book_metadata SET content_rating = NULLIF(TRIM(content_rating), '') WHERE content_rating IS NOT NULL AND content_rating != TRIM(content_rating);
UPDATE book_metadata SET narrator = NULLIF(TRIM(narrator), '') WHERE narrator IS NOT NULL AND narrator != TRIM(narrator);
