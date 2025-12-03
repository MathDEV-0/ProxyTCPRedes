package metrics;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

import optimizations.AlgorithmSelector;

public class TCPMetrics {

    private final Socket client;
    private final Socket server;

    private long totalClientBytes = 0;
    private long totalServerBytes = 0;

    private long lastRtt = -1;
    private double rttVar = 0;

    private final Object lock = new Object();
    private volatile boolean connectionAlive = true;

    private volatile boolean logging = false;
    private Thread loggingThread = null;
    private PrintWriter csvOut = null;

    private long lastBytes = 0;
    private long lastTs = -1;

    private String congestionAlgorithm = "unknown";

    private volatile int bufferSize = -1;

    private volatile int congestionWindow = -1;
    private volatile int ssthresh = 300;   // TCP Tahoe threshold
    private final int initCwnd = 10;       // CWND inicial

    public TCPMetrics(Socket client, Socket server) {
        this.client = client;
        this.server = server;
        detectOrMockCongestionAlgorithm();
        this.congestionWindow = initCwnd;
    }

    public void setBufferSize(int size) { this.bufferSize = size; }
    public int getBufferSize() { return bufferSize; }

    public long getTotalClientBytes() { synchronized (lock) { return totalClientBytes; } }
    public long getTotalServerBytes() { synchronized (lock) { return totalServerBytes; } }
    public long getLastRtt() { return lastRtt; }
    public double getRttVar() { return rttVar; }
    public int getCongestionWindow() { return congestionWindow; }
    public String getCongestionAlgorithm() { return congestionAlgorithm; }
    public void setCongestionAlgorithm(String algo) { this.congestionAlgorithm = algo; }

    public void recordBytes(String dir, int n) {
        synchronized (lock) {
            if ("C→S".equals(dir)) totalClientBytes += n;
            else totalServerBytes += n;
        }
    }

    private long computeThroughput(long nowBytes, long nowTs) {
        if (lastTs < 0) {
            lastTs = nowTs;
            lastBytes = nowBytes;
            return 0;
        }
        long diffBytes = nowBytes - lastBytes;
        long diffMs = nowTs - lastTs;
        if (diffMs <= 0) return 0;
        long thr = (diffBytes * 1000) / diffMs;
        lastTs = nowTs;
        lastBytes = nowBytes;
        return thr;
    }

    public long getThroughputBps() {
        long now = System.currentTimeMillis();
        long totalBytes;
        synchronized (lock) {
            totalBytes = totalClientBytes + totalServerBytes;
        }
        return computeThroughput(totalBytes, now);
    }

    public long getRTT() {
        try {
            if (server == null || !server.isConnected() || server.isClosed()) return -1;
            OutputStream out = server.getOutputStream();
            InputStream in = server.getInputStream();
            byte[] ping = {42};
            byte[] pong = new byte[1];
            long start = System.nanoTime();
            out.write(ping);
            out.flush();
            int read = in.read(pong);
            long end = System.nanoTime();
            if (read == 1) return (end - start) / 1000;
        } catch (Exception ignored) {}
        return -1;
    }

    private void updateRttVar(long newRtt) {
        if (newRtt < 0) return;
        if (lastRtt != -1) {
            double diff = Math.abs(newRtt - lastRtt);
            rttVar = 0.75 * rttVar + 0.25 * diff;
        } else rttVar = 0;
        lastRtt = newRtt;
    }

    public void startBackgroundLogging(String prefix, int intervalMs) {
        if (logging) return;
        logging = true;

        try {
            File logsDir = new File("logs");
            if (!logsDir.exists()) logsDir.mkdirs();
            long epoch = System.currentTimeMillis();
            String clean = prefix.replace("logs/", "").replace(".csv", "");
            String csvPath = "logs/" + clean + "_" + epoch + ".csv";
            csvOut = new PrintWriter(new FileWriter(csvPath, false));
            csvOut.println("epoch_ms,c2s_bytes,s2c_bytes,rtt_us,rttvar_us,throughput_Bps,status,algorithm,buffer_size,cwnd,ssthresh");
            csvOut.flush();
        } catch (Exception e) {
            System.err.println("[METRICS] erro criando CSV: " + e.getMessage());
            return;
        }

        loggingThread = new Thread(() -> {
            try {
                while (logging && connectionAlive) {
                    long now = System.currentTimeMillis();
                    long c2s, s2c;
                    synchronized (lock) {
                        c2s = totalClientBytes;
                        s2c = totalServerBytes;
                    }

                    long rtt = getRTT();
                    if (rtt >= 0) updateRttVar(rtt);
                    else rtt = (lastRtt >= 0) ? lastRtt : 1000; // fallback RTT 1ms

                    updateCongestionWindow();

                    long thr = computeThroughput(c2s + s2c, now);
                    String status = rtt >= 0 ? "OK" : "FAIL";

                    synchronized (lock) {
                        csvOut.printf(
                            "%d,%d,%d,%d,%d,%d,%s,%s,%d,%d,%d%n",
                            now, c2s, s2c, rtt, (long) rttVar,
                            thr, status, getCongestionAlgorithm(),
                            bufferSize, congestionWindow, ssthresh
                        );
                        csvOut.flush();
                    }
                    Thread.sleep(intervalMs);
                }
            } catch (InterruptedException ignored) {}
            finally {
                synchronized (lock) {
                    if (csvOut != null) { csvOut.flush(); csvOut.close(); csvOut = null; }
                }
            }
        }, "CSV-Logger");

        loggingThread.setDaemon(true);
        loggingThread.start();
    }

    AlgorithmSelector selector = new AlgorithmSelector(this);

    public void monitor() {
        long lastC2S = 0, lastS2C = 0;

        try {
            while (connectionAlive) {
                long rtt = getRTT();
            if (rtt >= 0) updateRttVar(rtt);
            else rtt = (lastRtt >= 0) ? lastRtt : 1000;

            updateCongestionWindow();

            long c2s, s2c;
            int cwnd;
            synchronized (lock) {
                c2s = totalClientBytes;
                s2c = totalServerBytes;
                cwnd = congestionWindow;
            }

            // só imprime se houver bytes novos
            if (c2s != lastC2S || s2c != lastS2C) {
                System.out.println("[METRICS] C->S=" + c2s +
                        " | S->C=" + s2c +
                        " | RTT=" + rtt + "µs" +
                        " | RTTVAR=" + (long) rttVar +
                        " | BUFFER=" + bufferSize +
                        " | CWND=" + cwnd +
                        " | SSTHRESH=" + ssthresh);
                lastC2S = c2s;
                lastS2C = s2c;
            }

            Thread.sleep(500); // mantém intervalo de verificação
            } 
    }catch (Exception ignored) {}
        stopBackgroundLogging();
    }

    public void stopBackgroundLogging() {
        logging = false;
        if (loggingThread != null) loggingThread.interrupt();
        synchronized (lock) {
            if (csvOut != null) { csvOut.flush(); csvOut.close(); csvOut = null; }
        }
    }

    public void close() {
        connectionAlive = false;
        stopBackgroundLogging();
    }

    public void detectOrMockCongestionAlgorithm() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (!os.contains("linux")) throw new UnsupportedOperationException("Not Linux");

            Process p = Runtime.getRuntime().exec("cat /proc/sys/net/ipv4/tcp_congestion_control");
            p.waitFor();
            try (Scanner sc = new Scanner(p.getInputStream())) {
                if (sc.hasNext()) {
                    congestionAlgorithm = sc.next().trim().toUpperCase();
                    System.out.println("[TCPMetrics] Detected Linux TCP CC = " + congestionAlgorithm);
                    return;
                }
            }
            throw new Exception("empty result");
        } catch (Exception e) {
            congestionAlgorithm = "WINDOWS-MOCK";
            System.out.println("[TCPMetrics] Falling back to Windows-safe congestion control mock");
        }
    }

    public void debugConnectionInfo() {
        System.out.println("\n=== DEBUG CONNECTION INFO ===");
        System.out.println("Client Socket:");
        System.out.println("  Local: " + client.getLocalAddress() + ":" + client.getLocalPort());
        System.out.println("  Remote: " + client.getInetAddress() + ":" + client.getPort());
        System.out.println("  Connected: " + client.isConnected());
        System.out.println("  Closed: " + client.isClosed());
        if (server != null) {
            System.out.println("\nServer Socket:");
            System.out.println("  Local: " + server.getLocalAddress() + ":" + server.getLocalPort());
            System.out.println("  Remote: " + server.getInetAddress() + ":" + server.getPort());
            System.out.println("  Connected: " + server.isConnected());
            System.out.println("  Closed: " + server.isClosed());
        }
        System.out.println("\nCurrent Metrics:");
        System.out.println("  CWND: " + congestionWindow);
        System.out.println("  SSTHRESH: " + ssthresh);
        System.out.println("  Buffer Size: " + bufferSize);
        System.out.println("  Algorithm: " + congestionAlgorithm);
        System.out.println("  Total C->S: " + getTotalClientBytes() + " bytes");
        System.out.println("  Total S->C: " + getTotalServerBytes() + " bytes");
        System.out.println("  Throughput: " + getThroughputBps() + " B/s");
        System.out.println("================================\n");
    }

    // -------------------- CWND Update --------------------

    public void updateCongestionWindow() {
        int measuredCwnd = fetchCwndFromLinux();
        if (measuredCwnd > 0) {
            congestionWindow = measuredCwnd;
            return;
        }
        // fallback: TCP Tahoe logic
        congestionWindow = tcpTahoeLogic(congestionWindow, ssthresh);
    }

    private int fetchCwndFromLinux() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (!os.contains("linux")) return -1;

            String localIp = client.getLocalAddress().getHostAddress();
            int localPort = client.getLocalPort();
            String remoteIp = client.getInetAddress().getHostAddress();
            int remotePort = client.getPort();

            Process p = Runtime.getRuntime().exec(
                    String.format("ss -ti src %s:%d dst %s:%d", localIp, localPort, remoteIp, remotePort)
            );

            try (Scanner sc = new Scanner(p.getInputStream())) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    if (line.contains("cwnd:")) {
                        String[] parts = line.split("\\s+");
                        for (String token : parts) {
                            if (token.startsWith("cwnd:")) return Integer.parseInt(token.substring(5));
                        }
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}
        return -1;
    }

    private int tcpTahoeLogic(int cwnd, int ssthresh) {
        if (cwnd < ssthresh) cwnd = Math.min(cwnd * 2, ssthresh);
        else cwnd += 1;

        if (rttVar > 500_000 && getLastRtt() > 1_000_000) {
            ssthresh = Math.max(cwnd / 2, 10);
            cwnd = initCwnd;
            System.out.println("[TCP-TAHOE] Perda detectada: reset cwnd=" + cwnd + " ssthresh=" + ssthresh);
        }
        return cwnd;
    }
}
