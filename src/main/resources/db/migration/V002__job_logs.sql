CREATE TABLE job_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_run_id INTEGER,
    level TEXT NOT NULL,
    message TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY(job_run_id) REFERENCES job_runs(id) ON DELETE SET NULL
);

CREATE INDEX idx_job_logs_created_at ON job_logs(created_at);
CREATE INDEX idx_job_logs_job_run_id ON job_logs(job_run_id);
