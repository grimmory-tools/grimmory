-- Normalize goodreads_id values that contain slug suffixes (e.g. "52555538-dead-simple-python" → "52555538")
UPDATE book_metadata
SET goodreads_id = SUBSTRING_INDEX(goodreads_id, '-', 1)
WHERE goodreads_id IS NOT NULL
  AND goodreads_id REGEXP '^[0-9]+-'
  AND SUBSTRING_INDEX(goodreads_id, '-', 1) REGEXP '^[0-9]+$';

UPDATE book_metadata
SET goodreads_id = SUBSTRING_INDEX(goodreads_id, '.', 1)
WHERE goodreads_id IS NOT NULL
  AND goodreads_id REGEXP '^[0-9]+\\.'
  AND SUBSTRING_INDEX(goodreads_id, '.', 1) REGEXP '^[0-9]+$';
