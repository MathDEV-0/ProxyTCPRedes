package optimizations;

import metrics.TCPMetrics;

public class DelayPolicy implements TCPOpPolicy {

    private final TCPMetrics metrics;

    public DelayPolicy(TCPMetrics m) {
        this.metrics = m;
    }

    @Override
    public void apply(byte[] buf, int len) {
        try {
            long delay = len / 2000; // pacing simples
            if (delay > 0) Thread.sleep(delay);
        } catch (Exception ignored) {}
    }
}
