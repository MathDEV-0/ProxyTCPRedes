package optimizations;

import metrics.TCPMetrics;

public class DelayedAckPolicy implements TCPOpPolicy {

    private final TCPMetrics metrics;

    public DelayedAckPolicy(TCPMetrics m) {
        this.metrics = m;
    }

    @Override
    public void apply(byte[] buf, int len) {
        try { Thread.sleep(1); } catch (Exception ignored) {}
    }
}
