package network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ServidorDireto {
    private final int port;

    public ServidorDireto(int port) {
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[DIRECT-SERVER] Escutando na porta " + port);

            while (true) {
                Socket client = serverSocket.accept();
                System.out.println("[DIRECT-SERVER] Cliente conectado: " + client);
                new Thread(() -> handleClient(client)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket client) {
        long totalReceived = 0;
        long totalSent = 0;
        long lastRtt = -1;
        double rttVar = 0;

        byte[] buf = new byte[8192];

        // CSV
        File logDir = new File("logs");
        if (!logDir.exists()) logDir.mkdirs();

        try (PrintWriter csv = new PrintWriter(new FileWriter("logs/server_direct.csv", true))) {
            csv.println("epoch_ms,c2s_bytes,s2c_bytes,rtt_us,rttvar_us,throughput_Bps,status");

            long lastTime = System.currentTimeMillis();
            long lastTotal = 0;

            try (InputStream in = client.getInputStream();
                 OutputStream out = client.getOutputStream()) {

                while (true) {
                    int len = in.read(buf);
                    if (len == -1) break;
                    totalReceived += len;

                    // ecoa
                    out.write(buf, 0, len);
                    out.flush();
                    totalSent += len;

                    // RTT estimado (ping-pong)
                    try {
                        byte[] ping = {42};
                        byte[] pong = new byte[1];
                        long start = System.nanoTime();
                        out.write(ping);
                        out.flush();
                        in.read(pong);
                        long end = System.nanoTime();
                        long rtt = (end - start) / 1000; // µs
                        if (lastRtt != -1) rttVar = 0.75 * rttVar + 0.25 * Math.abs(rtt - lastRtt);
                        lastRtt = rtt;
                    } catch (Exception ignored) {}

                    long now = System.currentTimeMillis();
                    if (now - lastTime >= 1000) {
                        long throughput = ((totalReceived + totalSent) - lastTotal) * 1000 / (now - lastTime);
                        lastTotal = totalReceived + totalSent;

                        String status = "OK";

                        // CSV
                        csv.printf("%d,%d,%d,%d,%.0f,%d,%s%n",
                                now, totalReceived, totalSent, lastRtt, rttVar, throughput, status);
                        csv.flush();

                        // Print terminal
                        System.out.println("[METRICS] C->S=" + totalReceived +
                                " | S->C=" + totalSent +
                                " | RTT=" + lastRtt + "µs" +
                                " | RTTVAR=" + (long) rttVar +
                                " | Throughput=" + throughput + " B/s");

                        lastTime = now;
                    }
                }

            }
        } catch (Exception e) {
            System.out.println("[DIRECT-SERVER] Cliente desconectado ou erro: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new ServidorDireto(9001).start();
    }
}
