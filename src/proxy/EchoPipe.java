package proxy;

import metrics.TCPMetrics;
import optimizations.TCPOpPolicy;
import metrics.PCAPWriter;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;

public class EchoPipe implements Runnable {

    private final Socket client;
    private final TCPMetrics metrics;
    private final Object lock;
    private final PCAPWriter pcap;

    public EchoPipe(Socket client, TCPMetrics metrics, Object lock) throws Exception {
        this.client = client;
        this.metrics = metrics;
        this.lock = lock;

        String fileName = "pcap/echo_" + Instant.now().toEpochMilli() + ".pcap";
        this.pcap = new PCAPWriter(fileName);
    }

    @Override
    public void run() {
        try (InputStream in = client.getInputStream();
             OutputStream out = client.getOutputStream()) {

            byte[] buffer = new byte[8192];
            int read;

            while ((read = in.read(buffer)) != -1) {
                synchronized (lock) {
                    // grava pcap
                    pcap.writePacket(buffer, read);

                    // reenvia exatamente o que recebeu
                    out.write(buffer, 0, read);
                    out.flush();
                }
                metrics.recordBytes("ECHO", read);
            }

        } catch (Exception e) {
            System.out.println("[ECHO] Cliente desconectado: " + e.getMessage());
        } finally {
            metrics.close();
            try { pcap.close(); } catch (Exception ignored) {}
        }
    }
}
