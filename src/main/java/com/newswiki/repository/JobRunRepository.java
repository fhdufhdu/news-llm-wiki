package com.newswiki.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class JobRunRepository {
    private final EntityManager entityManager;

    @Transactional
    public long start(String jobType, int inputCount) {
        entityManager.createNativeQuery("""
                insert into job_runs(job_type, status, started_at, input_count)
                values (:jobType, 'RUNNING', :startedAt, :inputCount)
                """)
                .setParameter("jobType", jobType)
                .setParameter("startedAt", Instant.now().toString())
                .setParameter("inputCount", inputCount)
                .executeUpdate();
        return longValue(entityManager.createNativeQuery("select last_insert_rowid()").getSingleResult());
    }

    @Transactional
    public void finish(long id, String status, int outputCount, Integer exitCode, String errorMessage) {
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (var statement = connection.prepareStatement("""
                    update job_runs
                       set status = ?,
                           finished_at = ?,
                           output_count = ?,
                           exit_code = ?,
                           error_message = ?
                     where id = ?
                    """)) {
                statement.setString(1, status);
                statement.setString(2, Instant.now().toString());
                statement.setInt(3, outputCount);
                if (exitCode == null) {
                    statement.setNull(4, Types.INTEGER);
                } else {
                    statement.setInt(4, exitCode);
                }
                if (errorMessage == null) {
                    statement.setNull(5, Types.VARCHAR);
                } else {
                    statement.setString(5, errorMessage);
                }
                statement.setLong(6, id);
                statement.executeUpdate();
            }
        });
    }

    @Transactional(readOnly = true)
    public List<JobRunView> findRecent(int limit) {
        return entityManager.createNativeQuery("""
                select id, job_type, status, started_at, finished_at, input_count, output_count, exit_code, error_message
                  from job_runs
                 order by started_at desc
                 limit :limit
                """)
                .setParameter("limit", limit)
                .getResultStream()
                .map(row -> {
                    Object[] values = (Object[]) row;
                    return new JobRunView(
                            longValue(values[0]),
                            stringValue(values[1]),
                            stringValue(values[2]),
                            stringValue(values[3]),
                            stringValue(values[4]),
                            intValue(values[5]),
                            intValue(values[6]),
                            values[7] == null ? null : intValue(values[7]),
                            stringValue(values[8])
                    );
                })
                .toList();
    }

    @Transactional
    public void appendLog(Long jobRunId, String level, String message) {
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (var statement = connection.prepareStatement("""
                    insert into job_logs(job_run_id, level, message, created_at)
                    values (?, ?, ?, ?)
                    """)) {
                if (jobRunId == null) {
                    statement.setNull(1, Types.BIGINT);
                } else {
                    statement.setLong(1, jobRunId);
                }
                statement.setString(2, level);
                statement.setString(3, message);
                statement.setString(4, Instant.now().toString());
                statement.executeUpdate();
            }
        });
    }

    @Transactional(readOnly = true)
    public List<JobLogView> findRecentLogs(int limit) {
        return entityManager.createNativeQuery("""
                select id, job_run_id, level, message, created_at
                  from (
                        select id, job_run_id, level, message, created_at
                          from job_logs
                         order by created_at desc, id desc
                         limit :limit
                       )
                 order by created_at asc, id asc
                """)
                .setParameter("limit", limit)
                .getResultStream()
                .map(row -> {
                    Object[] values = (Object[]) row;
                    return new JobLogView(
                            longValue(values[0]),
                            values[1] == null ? null : longValue(values[1]),
                            stringValue(values[2]),
                            stringValue(values[3]),
                            stringValue(values[4])
                    );
                })
                .toList();
    }

    private static long longValue(Object value) {
        return ((Number) value).longValue();
    }

    private static int intValue(Object value) {
        return ((Number) value).intValue();
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public record JobRunView(
            long id,
            String jobType,
            String status,
            String startedAt,
            String finishedAt,
            int inputCount,
            int outputCount,
            Integer exitCode,
            String errorMessage
    ) {
    }

    public record JobLogView(
            long id,
            Long jobRunId,
            String level,
            String message,
            String createdAt
    ) {
        public String line() {
            return createdAt + " [" + level + "] " + message;
        }
    }
}
