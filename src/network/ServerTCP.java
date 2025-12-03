package network;


import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerTCP implements Runnable {
    private int port;

    public ServerTCP(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[SERVER] Escutando na porta " + port);

            while (true) {
                Socket client = serverSocket.accept();
                System.out.println("[SERVER] Cliente conectado: " + client);

                new Thread(() -> handleClient(client)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket client) {
        try (InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream()) {

            byte[] buffer = new byte[8192]; // buffer de leitura
            int bytesRead;
            long totalBytes = 0;
            long bytesSinceLastLog = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                totalBytes += bytesRead;
                bytesSinceLastLog += bytesRead;

                // eco para o cliente
                out.write(buffer, 0, bytesRead);
                out.flush();

                // preview do chunk (máximo 50 chars)
                int previewLen = Math.min(bytesRead, 50);
                String chunkPreview = new String(buffer, 0, previewLen);
                System.out.println("[SERVER] Chunk recebido (" + bytesRead + " bytes): " + chunkPreview);

                // log periódico a cada 1KB
                if (bytesSinceLastLog >= 1024) {
                    System.out.println("[SERVER] Total recebido até agora: " + totalBytes + " bytes");
                    bytesSinceLastLog = 0;
                }
            }

            // log final
            System.out.println("[SERVER] Conexão encerrada. Total recebido: " + totalBytes + " bytes");

        } catch (Exception e) {
            System.out.println("[SERVER] Cliente desconectado abruptamente: " + client);
        }
    }





}

