package com.uws.transaction_service.scheduler;


import com.uws.transaction_service.repository.TransactionLogRepository;
import com.uws.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionScheduler {

    private final TransactionRepository transactionRepository;
    private final TransactionLogRepository transactionLogRepository;

    @Value("${transaction.log.cleanup.threshold.days:30}")
    private int thresholdInDays;

    @Scheduled(cron = "0 0 0 28 * *")
    public void cleanUpOldTransactionLogs() {
        log.info("Starting cleanup of old transaction logs...");

        // Define the threshold for old logs (e.g., 30 days)
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(thresholdInDays);

        // Delete logs older than the threshold
        int deletedCount = transactionLogRepository.deleteLogsOlderThan(cutoffDate);

        log.info("Cleanup completed. Deleted {} old transaction logs.", deletedCount);
    }








}
