package proxy;

import java.net.ServerSocket;
import java.net.Socket;

public class ProxyTCP implements Runnable {

    private final int listenPort;
    private final String targetHost;
    private final int targetPort;

    public ProxyTCP(int listenPort, String targetHost, int targetPort) {
        this.listenPort = listenPort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    @Override
    public void run() {
        try (ServerSocket ss = new ServerSocket(listenPort)) {
            System.out.println("[PROXY] Escutando na porta " + listenPort);

            while (true) {
                Socket client = ss.accept();
                System.out.println("[PROXY] Cliente conectado");

                new Thread(new ProxyHandler(client, targetHost, targetPort)).start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
