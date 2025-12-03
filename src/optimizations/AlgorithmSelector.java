package optimizations;

import metrics.TCPMetrics;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class AlgorithmSelector implements TCPOpPolicy {

    private final TCPMetrics metrics;

    private long pacingDelayMs = 0;
    private long ackDelayUs = 0;
    private int sendBufferSize = 4 * 1024;

    private OpPolicyType current = OpPolicyType.BALANCED;
    private long lastSwitch = 0;

    private final boolean isLinux;

    public AlgorithmSelector(TCPMetrics metrics) {
        this.metrics = metrics;
        this.isLinux = System.getProperty("os.name").toLowerCase().contains("linux");
    }

    @Override
    public void apply(byte[] buf, int len) {
        long now = System.currentTimeMillis();
        if (now - lastSwitch < 2500) return;

        long rtt = metrics.getLastRtt();
        double var = metrics.getRttVar();
        long thr = metrics.getThroughputBps();

        OpPolicyType chosen = pickPolicy(rtt, var, thr);

        if (chosen != current) {
            current = chosen;
            applyNetworkTuning(chosen);
        }

        lastSwitch = now;
    }

    public OpPolicyType getCurrentPolicy() {
        return current;
    }

    /** Determina política baseada nos últimos valores de RTT, variação e throughput */
    private OpPolicyType pickPolicy(long rtt, double var, long thr) {
        if (rtt > 5_000_000 || var > 3_000_000) return OpPolicyType.SAFE;
        if (var > 900_000 || thr < 30_000) return OpPolicyType.CONSERVATIVE;
        if (rtt < 700_000 && var < 100_000 && thr > 100_000) return OpPolicyType.AGGRESSIVE;
        return OpPolicyType.BALANCED;
    }

    /** Aplica ajustes de rede dependendo da política */
    private void applyNetworkTuning(OpPolicyType policy) {

        String algo;

        int minBuf = 1 * 1024;
        int maxBufAggressive = 256 * 1024;
        int maxBufBalanced = 128 * 1024;
        int maxBufConservative = 64 * 1024;
        int maxBufSafe = 32 * 1024;

        int prevBuf = sendBufferSize;

        switch (policy) {
            case AGGRESSIVE:
                pacingDelayMs = 0;
                ackDelayUs = 15_000;
                algo = "bbr";
                sendBufferSize = adjustBuffer(sendBufferSize, 8*1024, maxBufAggressive, minBuf);
                break;

            case BALANCED:
                pacingDelayMs = 1;
                ackDelayUs = 40_000;
                algo = "cubic";
                sendBufferSize = adjustBuffer(sendBufferSize, 4*1024, maxBufBalanced, minBuf);
                break;

            case CONSERVATIVE:
                pacingDelayMs = 3;
                ackDelayUs = 120_000;
                algo = "reno";
                sendBufferSize = adjustBuffer(sendBufferSize, 2*1024, maxBufConservative, minBuf);
                break;

            case SAFE:
            default:
                pacingDelayMs = 8;
                ackDelayUs = 200_000;
                algo = "westwood";
                sendBufferSize = adjustBuffer(sendBufferSize, 1*1024, maxBufSafe, minBuf);
                break;
        }

        metrics.setBufferSize(sendBufferSize);

        if (isLinux) {
            boolean ok = setLinuxCongestionAlgorithm(algo);
            if (!ok) metrics.setCongestionAlgorithm("LINUX-FAILED-" + algo.toUpperCase());
            else metrics.setCongestionAlgorithm(algo.toUpperCase());
        } else metrics.setCongestionAlgorithm("MOCK-" + algo.toUpperCase());

        System.out.println("[ALG] Policy=" + policy +
                " TCP=" + algo +
                " pacing=" + pacingDelayMs + "ms" +
                " ack=" + ackDelayUs + "us" +
                " buf=" + prevBuf + "->" + sendBufferSize +
                (isLinux ? " (sysctl REAL)" : " (mock)"));
    }

    /** Ajusta buffer gradualmente, sobe ou desce dependendo do limite da política */
    private int adjustBuffer(int current, int step, int max, int min) {
        if (current < max) current += step;
        else if (current > max) current -= step;
        if (current < min) current = min;
        return current;
    }

    /** Troca algoritmo TCP no Linux via sysctl */
    private boolean setLinuxCongestionAlgorithm(String algo) {
        try {
            Process p = new ProcessBuilder("sysctl",
                    "-w", "net.ipv4.tcp_congestion_control=" + algo)
                    .redirectErrorStream(true)
                    .start();

            p.waitFor();

            Process check = new ProcessBuilder("sysctl",
                    "net.ipv4.tcp_congestion_control")
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(check.getInputStream()))) {
                String line = br.readLine();
                if (line != null && line.contains(algo)) {
                    return true;
                }
            }

        } catch (Exception e) {
            System.err.println("[SYSCTL] ERRO ao trocar algoritmo: " + e.getMessage());
        }

        return false;
    }

    public long getPacingDelayMs() { return pacingDelayMs; }
    public long getAckDelayUs() { return ackDelayUs; }
    public int getSendBufferSize() { return sendBufferSize; }
}
