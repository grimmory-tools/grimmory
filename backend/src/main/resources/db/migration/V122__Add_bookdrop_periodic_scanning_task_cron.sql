INSERT INTO task_cron_configuration (task_type, cron_expression, enabled, created_by)
SELECT 'BOOKDROP_PERIODIC_SCANNING', '0 */10 * * * *', FALSE, -1
WHERE NOT EXISTS (
    SELECT 1 FROM task_cron_configuration WHERE task_type = 'BOOKDROP_PERIODIC_SCANNING'
);
