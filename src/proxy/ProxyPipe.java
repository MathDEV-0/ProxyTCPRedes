package proxy;

import metrics.TCPMetrics;
import optimizations.TCPOpPolicy;
import metrics.PCAPWriter;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;

public class ProxyPipe implements Runnable {

    private final Socket inSock;
    private final Socket outSock;
    private final String name;
    private final TCPMetrics metrics;
    private final TCPOpPolicy policy;
    private final Object lock;

    private final PCAPWriter pcap;

    //Mude para false se quiser desabilitar as otimizações
    private final boolean enableOptimization = true;

    public ProxyPipe(Socket in, Socket out, String name,
                     TCPMetrics metrics, TCPOpPolicy policy, Object lock) throws Exception {

        this.inSock = in;
        this.outSock = out;
        this.name = name;
        this.metrics = metrics;
        this.policy = policy;
        this.lock = lock;

        String safeName = name.replace("→", "_").replaceAll("[^a-zA-Z0-9_\\-\\.]", "_");
        String fileName = "pcap/" + safeName + "_" + Instant.now().toEpochMilli() + ".pcap";
        this.pcap = new PCAPWriter(fileName);
    }

    @Override
    public void run() {
        try (InputStream in = inSock.getInputStream();
             OutputStream out = outSock.getOutputStream()) {

            byte[] buffer = new byte[policy.getSendBufferSize()];
            int read;
            int flushCounter = 0;
            boolean optimApplied = false;

            while ((read = in.read(buffer)) != -1) {
                synchronized (lock) {
                    // grava somente os bytes válidos no pcap
                    pcap.writePacket(buffer, read);

                    // aplica otimizações apenas uma vez
                    if (enableOptimization) {
                        policy.apply(buffer, read);
                    }

                    // escreve no output
                    out.write(buffer, 0, read);
                    flushCounter++;
                    // flush a cada 8 chunks (~64KB)
                    if (flushCounter % 8 == 0) out.flush();
                }

                metrics.recordBytes(name, read);
            }

            // flush final para garantir envio de tudo
            try { out.flush(); } catch (Exception ignored) {}

        } catch (Exception e) {
            System.out.println("[PIPE] " + name + " encerrado. (" + e.getMessage() + ")");
        } finally {
            metrics.close();
            try { pcap.close(); } catch (Exception ignored) {}
        }
    }
}
