package git.yannynz.organizadorproducao.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Utilitário para medir o tempo gasto no processamento de mensagens de filas.
 */
@Component
public class MessageProcessingMetrics {

    private static final String TIMER_NAME = "organizador_message_processing_seconds";

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();

    public MessageProcessingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordProcessing(String queue, ThrowingRunnable runnable) throws Exception {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            runnable.run();
        } finally {
            sample.stop(resolveTimer(queue));
        }
    }

    public <T> T recordProcessing(String queue, ThrowingSupplier<T> supplier) throws Exception {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return supplier.get();
        } finally {
            sample.stop(resolveTimer(queue));
        }
    }

    private Timer resolveTimer(String queue) {
        return timers.computeIfAbsent(queue,
            key -> Timer.builder(TIMER_NAME)
                .description("Tempo para processar mensagens nas filas RabbitMQ")
                .tag("queue", key)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry));
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
