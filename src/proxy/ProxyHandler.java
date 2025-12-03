package proxy;

import metrics.TCPMetrics;
import optimizations.TCPOpPolicy;

import java.net.Socket;

public class ProxyHandler implements Runnable {

    private final Socket clientSocket;
    private final Socket serverSocket;

    private final Object sendLock = new Object(); // mutex global para envio

    private final TCPMetrics metrics;
    private final TCPOpPolicy policy;

    public ProxyHandler(Socket client, String host, int port) throws Exception {
        this.clientSocket = client;
        this.serverSocket = new Socket(host, port);

        this.metrics = new TCPMetrics(clientSocket, serverSocket);
        this.policy  = TCPOpPolicy.defaultPolicies(metrics);
    }

    @Override
    public void run() {
        try {
            // ---------- MONITORAMENTO DAS MÉTRICAS ----------
            Thread monitor = new Thread(() -> metrics.monitor(), "Metrics-Monitor");
            monitor.setDaemon(true); // não impede o programa de fechar
            monitor.start();

            // inicia logger CSV em background (opcional) - cria logs/<epoch>.csv
            metrics.startBackgroundLogging("logs/metrics.csv", 500);
            // -------------------------------------------------

            Thread c2s = new Thread(
                new ProxyPipe(clientSocket, serverSocket, "C→S", metrics, policy, sendLock),
                "Pipe-C2S"
            );

            Thread s2c = new Thread(
                new ProxyPipe(serverSocket, clientSocket, "S→C", metrics, policy, sendLock),
                "Pipe-S2C"
            );

            c2s.start();
            s2c.start();

            c2s.join();
            s2c.join();

            Thread echo = new Thread(new EchoPipe(clientSocket, metrics, sendLock), "Echo-Pipe");
            echo.start();
            echo.join();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // garante que a conexão e os loggers sejam fechados
            metrics.close();
        }
    }
}
