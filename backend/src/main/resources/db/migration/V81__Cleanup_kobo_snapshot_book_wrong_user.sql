DELETE ksb
FROM kobo_library_snapshot_book ksb
JOIN kobo_library_snapshot ks ON ks.id = ksb.snapshot_id
LEFT JOIN book b ON b.id = ksb.book_id
LEFT JOIN user_library_mapping ulm
    ON ulm.library_id = b.library_id
   AND ulm.user_id = ks.user_id
WHERE ulm.user_id IS NULL;
