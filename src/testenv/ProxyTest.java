package testenv;

import proxy.ProxyHandler;
import optimizations.TCPOpPolicy;
import optimizations.DelayPolicy;
import optimizations.DelayedAckPolicy;
import metrics.TCPMetrics;

import java.net.ServerSocket;
import java.net.Socket;

/**
 * Teste de Proxy TCP com políticas de atraso.
 *
 * Cenários:
 * 1. Conexão direta (sem proxy)
 * 2. Conexão via proxy sem otimização
 * 3. Conexão via proxy com otimização (DelayPolicy + DelayedAckPolicy)
 *
 * Use tc (Linux) para simular atraso, perda e limitação de banda se desejar.
 */
public class ProxyTest {

    public static void main(String[] args) throws Exception {

        int serverPort = 9000;
        int proxyPort  = 8000;

        // -------------------------------
        // 1️⃣ Servidor TCP (echo simples)
        // -------------------------------
        Thread serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
                System.out.println("[TEST] Servidor rodando na porta " + serverPort);
                while (true) {
                    Socket client = serverSocket.accept();
                    System.out.println("[TEST] Cliente conectado: " + client);
                    byte[] buf = new byte[1024];
                    int read;
                    while ((read = client.getInputStream().read(buf)) != -1) {
                        client.getOutputStream().write(buf, 0, read);
                        client.getOutputStream().flush();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "ServerThread");

        // não daemon para manter o programa vivo
        serverThread.start();

        // -------------------------------
        // 2️⃣ Proxy sem otimização
        // -------------------------------
        Thread proxyThreadNoOpt = new Thread(() -> {
            try (ServerSocket proxyListener = new ServerSocket(proxyPort)) {
                System.out.println("[TEST] Proxy (sem otimização) rodando na porta " + proxyPort);
                while (true) {
                    Socket clientSock = proxyListener.accept();
                    Socket serverSock = new Socket("localhost", serverPort);
                    TCPMetrics metrics = new TCPMetrics(clientSock, serverSock);

                    // Política NULA = sem atraso
                    TCPOpPolicy noPolicy = new TCPOpPolicy() {
                        @Override
                        public void apply(byte[] buf, int len) { /* sem alteração */ }
                    };

                    new Thread(new ProxyHandler(clientSock, "localhost", serverPort) {
                        @Override
                        public void run() {
                            try {
                                metrics.startBackgroundLogging("logs/noopt.csv", 1000);
                                super.run();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "ProxyNoOpt");

        proxyThreadNoOpt.start();

        // -------------------------------
        // 3️⃣ Proxy com otimização (Delay + Delayed ACK)
        // -------------------------------
        Thread proxyThreadOpt = new Thread(() -> {
            try (ServerSocket proxyListener = new ServerSocket(proxyPort + 1)) {
                System.out.println("[TEST] Proxy (com otimização) rodando na porta " + (proxyPort + 1));
                while (true) {
                    Socket clientSock = proxyListener.accept();
                    Socket serverSock = new Socket("localhost", serverPort);
                    TCPMetrics metrics = new TCPMetrics(clientSock, serverSock);

                    // Política combinada: TCP pacing + Delayed ACK
                    TCPOpPolicy optimizedPolicy = new TCPOpPolicy() {
                        private final DelayPolicy pacing = new DelayPolicy(metrics);
                        private final DelayedAckPolicy delayed = new DelayedAckPolicy(metrics);

                        @Override
                        public void apply(byte[] buf, int len) {
                            pacing.apply(buf, len);       // limita taxa de envio
                            delayed.apply(buf, len);      // atrasa ACKs
                        }
                    };

                    new Thread(new ProxyHandler(clientSock, "localhost", serverPort) {
                        @Override
                        public void run() {
                            try {
                                metrics.startBackgroundLogging("logs/opt.csv", 1000);
                                super.run();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "ProxyOpt");

        proxyThreadOpt.start();

        // -------------------------------
        // Mantém o programa vivo
        // -------------------------------
        System.out.println("[TEST] Todos os proxies iniciados. Conecte clientes nas portas 8000 (sem otimização) ou 8001 (com otimização).");
        while (true) {
            Thread.sleep(1000); // loop infinito para manter o main thread ativo
        }
    }
}
