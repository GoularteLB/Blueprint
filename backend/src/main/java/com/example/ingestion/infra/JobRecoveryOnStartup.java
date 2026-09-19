package com.example.ingestion.infra;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JobRecoveryOnStartup {

    private static final Logger log = LoggerFactory.getLogger(JobRecoveryOnStartup.class);

    private final JdbcTemplate jdbc;

    public JobRecoveryOnStartup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void recoverOrphans() {
        int n = jdbc.update("""
                UPDATE import_job
                   SET status = 'FAILED',
                       error_message = 'Worker interrupted by application restart',
                       finished_at = now()
                 WHERE status IN ('PENDING','RUNNING')
                """);
        if (n > 0) {
            log.warn("Marked {} orphaned import jobs as FAILED", n);
        }
    }
}
