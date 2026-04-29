-- Scheduler and poller hot paths
CREATE INDEX idx_wanted_book_status ON wanted_book (status);
CREATE INDEX idx_wanted_book_download_id ON wanted_book (download_id);
CREATE INDEX idx_acquisition_job_history_wanted_attempted ON acquisition_job_history (wanted_book_id, attempted_at);
