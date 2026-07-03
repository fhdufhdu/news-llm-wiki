package com.newswiki.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class JobLockService {
    private final EntityManager entityManager;

    @Transactional
    public boolean tryAcquire(String name, String owner, Duration ttl) {
        Instant now = Instant.now();
        Instant until = now.plus(ttl);
        int updated = entityManager.createNativeQuery("""
                update job_locks
                   set locked_until = :until,
                       locked_by = :owner,
                       updated_at = :now
                 where name = :name
                   and locked_until < :now
                """)
                .setParameter("until", until.toString())
                .setParameter("owner", owner)
                .setParameter("now", now.toString())
                .setParameter("name", name)
                .executeUpdate();
        if (updated == 1) {
            return true;
        }

        int inserted = entityManager.createNativeQuery("""
                insert or ignore into job_locks(name, locked_until, locked_by, updated_at)
                values (:name, :until, :owner, :now)
                """)
                .setParameter("name", name)
                .setParameter("until", until.toString())
                .setParameter("owner", owner)
                .setParameter("now", now.toString())
                .executeUpdate();
        return inserted == 1;
    }

    @Transactional
    public void release(String name, String owner) {
        Instant now = Instant.now();
        entityManager.createNativeQuery("""
                update job_locks
                   set locked_until = :now,
                       updated_at = :now
                 where name = :name
                   and locked_by = :owner
                """)
                .setParameter("now", now.toString())
                .setParameter("name", name)
                .setParameter("owner", owner)
                .executeUpdate();
    }

    @Transactional
    public int expireAllActiveLocks() {
        Instant now = Instant.now();
        return entityManager.createNativeQuery("""
                update job_locks
                   set locked_until = :now,
                       updated_at = :now
                 where locked_until > :now
                """)
                .setParameter("now", now.toString())
                .executeUpdate();
    }
}
