ALTER TABLE `user_book_progress` 
ADD COLUMN `date_finished` timestamp NULL DEFAULT NULL,
ADD INDEX `idx_user_book_progress_date_finished` (`date_finished`);