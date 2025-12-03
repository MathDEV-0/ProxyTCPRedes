package network;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Scanner;

public class Client_Test {

    public void start(String host, int port) {
        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream();
             Scanner sc = new Scanner(System.in)) {

            System.out.println("[CLIENT] Conectado ao proxy em " + host + ":" + port);
            System.out.println("[CLIENT] Menu de Testes TCP:");
            System.out.println("  1 - Enviar teste padrão (vários tamanhos)");
            System.out.println("  2 - Enviar 50MB de dados grandes");
            System.out.println("  3 - Enviar streaming contínuo por X segundos");
            System.out.println("  4 - Enviar bursts de dados variados");
            System.out.println("  5 - Enviar 100MB de mensagens pequenas (teste rápido)");
            System.out.println("  6 - Enviar dados com controle de taxa");
            System.out.println("  7 - Iniciar chat com servidor");
            System.out.println("  0 - Sair");

            while (true) {
                System.out.print("\n> Escolha uma opção (0-7): ");
                String input = sc.nextLine().trim();

                if (input.equals("0")) {
                    System.out.println("[CLIENT] Encerrando sessão...");
                    break;
                }

                switch (input) {
                    case "1":
                        System.out.println("[CLIENT] Iniciando teste padrão...");
                        sendTestData(out, in);
                        break;

                    case "2":
                        System.out.println("[CLIENT] Enviando 50MB de dados grandes...");
                        sendBigData(out, in, 50);
                        break;

                    case "3":
                        System.out.print("[CLIENT] Informe duração do streaming em segundos: ");
                        int duracao = sc.nextInt();
                        sc.nextLine();
                        System.out.println("[CLIENT] Iniciando streaming por " + duracao + "s...");
                        sendStreamingData(out, in, duracao);
                        break;

                    case "4":
                        System.out.println("[CLIENT] Enviando bursts de dados variados...");
                        sendBurstData(out, in);
                        break;

                    case "5":
                        System.out.println("[CLIENT] Enviando 100MB de mensagens pequenas...");
                        sendSmallMessages(out, in, 100); // envio de 100 mensagens pequenas
                        break;

                    case "6":
                        System.out.print("[CLIENT] Informe o tamanho total em MB: ");
                        int tamanho = sc.nextInt();
                        sc.nextLine();
                        System.out.print("[CLIENT] Informe o tempo total em segundos: ");
                        int tempo = sc.nextInt();
                        sc.nextLine();
                        System.out.println("[CLIENT] Enviando " + tamanho + "MB em " + tempo + " segundos...");
                        sendControlledData(out, in, tamanho, tempo);
                        break;

                    case "7":
                        System.out.println("[CLIENT] Iniciando chat interativo com o servidor...");
                        startChat(out, in, sc);
                        break;

                    default:
                        System.out.println("[CLIENT] Opção inválida! Use apenas números de 0 a 7.");
                        break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // Função do chat
    private void startChat(OutputStream out, InputStream in, Scanner sc) throws Exception {
        System.out.println("[CLIENT] Chat iniciado! Digite 'sair' para encerrar.");

        byte[] buffer = new byte[8192];

        while (true) {
            System.out.print("Você: ");
            String msg = sc.nextLine();
            if (msg.equalsIgnoreCase("sair")) {
                System.out.println("[CLIENT] Chat encerrado.");
                break;
            }

            out.write(msg.getBytes());
            out.flush();

            int len = in.read(buffer);
            if (len > 0) {
                String response = new String(buffer, 0, len);
                System.out.println("Servidor: " + response);
            }
        }
    }

    private void sendTestData(OutputStream out, InputStream in) throws Exception {
        System.out.println("[CLIENT] Enviando dados de teste...");
        String[] testMessages = {
            "A".repeat(1000),
            "B".repeat(10000),
            "C".repeat(50000),
            "D".repeat(100000),
            "E".repeat(500000),
            "F".repeat(1000000)
        };

        for (String msg : testMessages) {
            byte[] data = msg.getBytes();

            // envia tamanho do chunk (4 bytes)
            out.write(ByteBuffer.allocate(4).putInt(data.length).array());

            // envia os dados
            out.write(data);
            out.flush();

            System.out.println("[CLIENT] Enviado: " + data.length / 1024 + " KB");
            Thread.sleep(50);
        }

        System.out.println("[CLIENT] Dados de teste completos!");
    }

    private void sendBigData(OutputStream out, InputStream in, int megabytes) throws Exception {
        System.out.println("[CLIENT] Preparando para enviar " + megabytes + " MB...");

        int chunkSize = 256 * 1024; // 256 KB por chunk
        byte[] chunk = new byte[chunkSize];
        for (int i = 0; i < chunk.length; i++) chunk[i] = (byte) ('A' + (i % 26));

        long totalBytes = 0;
        int chunksToSend = (megabytes * 1024 * 1024) / chunkSize;

        for (int i = 0; i < chunksToSend; i++) {

            // tamanho
            out.write(ByteBuffer.allocate(4).putInt(chunk.length).array());
            // dados
            out.write(chunk);
            out.flush();

            totalBytes += chunk.length;

            if ((i + 1) % 5 == 0)
                System.out.println("[CLIENT] Progresso BigData: " + (totalBytes / 1024) + " KB enviados");

            Thread.sleep(5);
        }

        System.out.println("[CLIENT] BigData concluído! Total enviado: " + (totalBytes / 1024) + " KB");
    }


    private void sendStreamingData(OutputStream out, InputStream in, int seconds) throws Exception {
        System.out.println("[CLIENT] Enviando streaming por " + seconds + "s...");

        int chunkSize = 32 * 1024; // 32 KB
        byte[] chunk = new byte[chunkSize];
        for (int i = 0; i < chunk.length; i++) chunk[i] = (byte) ('A' + (i % 26));

        long end = System.currentTimeMillis() + seconds * 1000L;
        long total = 0;

        while (System.currentTimeMillis() < end) {

            out.write(ByteBuffer.allocate(4).putInt(chunkSize).array());
            out.write(chunk);
            out.flush();

            total += chunkSize;

            if (total % (1024 * 1024) == 0)
                System.out.println("[CLIENT] Streaming enviou " + (total / 1024 / 1024) + " MB");

            Thread.sleep(2);
        }

        System.out.println("[CLIENT] Streaming finalizado! Total: " + (total / 1024) + " KB");
    }


    private void sendBurstData(OutputStream out, InputStream in) throws Exception {
        System.out.println("[CLIENT] Enviando bursts...");

        int[] burstSizes = {1024, 8192, 32768, 65536, 131072, 262144, 524288}; // 1 KB -> 512 KB

        for (int size : burstSizes) {
            System.out.println("[CLIENT] Burst de " + (size / 1024) + " KB");

            byte[] burst = new byte[size];
            for (int i = 0; i < burst.length; i++) burst[i] = (byte) ('A' + (i % 26));

            // dividir bursts grandes em chunks menores (máx 128 KB)
            int chunkSize = Math.min(128 * 1024, size);
            int chunks = (size + chunkSize - 1) / chunkSize;

            for (int rep = 0; rep < 4; rep++) { // repete burst algumas vezes
                for (int c = 0; c < chunks; c++) {
                    int offset = c * chunkSize;
                    int len = Math.min(chunkSize, size - offset);

                    // envia tamanho
                    out.write(ByteBuffer.allocate(4).putInt(len).array());
                    out.write(burst, offset, len);
                    out.flush();

                    Thread.sleep(3);
                }
            }

            Thread.sleep(200); // pausa entre tamanhos
        }

        System.out.println("[CLIENT] Bursts enviados!");
    }


    private void sendSmallMessages(OutputStream out, InputStream in, int count) throws Exception {
        System.out.println("[CLIENT] Enviando mensagens pequenas...");

        for (int i = 0; i < count; i++) {
            byte[] msg = ("MSG_" + i).getBytes();

            out.write(ByteBuffer.allocate(4).putInt(msg.length).array());
            out.write(msg);
            out.flush();

            System.out.println("[CLIENT] Pequena mensagem enviada: " + new String(msg));

            Thread.sleep(10);
        }
    }


    private void sendControlledData(OutputStream out, InputStream in, int megabytes, int seconds) throws Exception {
        System.out.println("[CLIENT] Enviando " + megabytes + " MB em " + seconds + " segundos...");
        long totalBytes = megabytes * 1024L * 1024L;
        int chunkSize = 64 * 1024;
        byte[] chunk = new byte[chunkSize];
        for (int i = 0; i < chunk.length; i++) chunk[i] = (byte) ('A' + (i % 26));
        long bytesSent = 0;
        long startTime = System.currentTimeMillis();
        long endTime = startTime + seconds * 1000L;

        while (bytesSent < totalBytes) {
            long remaining = totalBytes - bytesSent;
            int sendSize = (int) Math.min(chunkSize, remaining);
            out.write(chunk, 0, sendSize);
            out.flush();
            bytesSent += sendSize;

            long now = System.currentTimeMillis();
            long timeLeft = endTime - now;
            long bytesLeft = totalBytes - bytesSent;
            if (bytesLeft > 0 && timeLeft > 0) {
                long interval = (timeLeft * sendSize) / bytesLeft;
                if (interval > 0) Thread.sleep(interval);
            }
        }

        long actualTime = (System.currentTimeMillis() - startTime) / 1000;
        System.out.println("[CLIENT] Envio controlado completo em ~" + actualTime + " segundos!");
    }


}
