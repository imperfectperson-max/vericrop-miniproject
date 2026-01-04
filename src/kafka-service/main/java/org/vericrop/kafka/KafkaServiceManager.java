package org.vericrop.kafka;

import org.vericrop.kafka.consumers.LogisticsEventConsumer;
import org.vericrop.kafka.consumers.QualityAlertConsumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class KafkaServiceManager {
    private final ExecutorService executor;
    private LogisticsEventConsumer logisticsConsumer;
    private QualityAlertConsumer qualityAlertConsumer;

    public KafkaServiceManager() {
        this.executor = Executors.newFixedThreadPool(3);
    }

    public void startAllConsumers() {
        System.out.println("🚀 Starting all Kafka consumers...");

        // Start logistics consumer
        logisticsConsumer = new LogisticsEventConsumer("vericrop-logistics-group");
        executor.submit(logisticsConsumer::startConsuming);

        // Start quality alert consumer
        qualityAlertConsumer = new QualityAlertConsumer("vericrop-alerts-group");
        executor.submit(qualityAlertConsumer::startConsuming);

        System.out.println("✅ All Kafka consumers started");
    }

    public void stopAllConsumers() {
        System.out.println("🛑 Stopping all Kafka consumers...");

        if (logisticsConsumer != null) {
            logisticsConsumer.stop();
        }
        if (qualityAlertConsumer != null) {
            qualityAlertConsumer.stop();
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("✅ All Kafka consumers stopped");
    }
}