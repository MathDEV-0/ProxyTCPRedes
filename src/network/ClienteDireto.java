package network;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Scanner;

public class ClienteDireto {

    public static void main(String[] args) {
        String serverHost = resolve("tcp-server");
        int port = 9001;

        File logDir = new File("logs");
        if (!logDir.exists()) logDir.mkdirs();

        try (Socket socket = new Socket(serverHost, port);
             PrintWriter csv = new PrintWriter(new FileWriter("logs/client_direct.csv", true));
             Scanner sc = new Scanner(System.in)) {

            csv.println("epoch_ms,c2s_bytes,s2c_bytes,rtt_us,rttvar_us,throughput_Bps,status");

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[8192];

            long totalSent = 0;
            long totalReceived = 0;
            long lastRtt = -1;
            double rttVar = 0;
            long lastTime = System.currentTimeMillis();
            long lastTotal = 0;

            System.out.println("[CLIENT] Escolha um teste:");
            System.out.println("1 - Enviar mensagens de tamanhos variados");
            System.out.println("2 - Bursts de dados");
            System.out.println("3 - Chat interativo (digite palavras)");
            System.out.print("> ");

            String choice = sc.nextLine().trim();

            switch (choice) {
                case "1":
                    sendVariedSizes(out, in, csv, buffer, totalSent, totalReceived);
                    break;
                case "2":
                    sendBursts(out, in, csv, buffer, totalSent, totalReceived);
                    break;
                case "3":
                    interactiveChat(out, in, csv, buffer, totalSent, totalReceived, sc);
                    break;
                default:
                    System.out.println("[CLIENT] Opção inválida!");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendVariedSizes(OutputStream out, InputStream in, PrintWriter csv, byte[] buffer,
                                        long totalSent, long totalReceived) throws Exception {
        String[] messages = {
                "A".repeat(1000),
                "B".repeat(10000),
                "C".repeat(50000),
                "D".repeat(100000),
                "E".repeat(500000),
                "F".repeat(1000000)
        };

        for (String msg : messages) {
            byte[] data = msg.getBytes();

            // envia tamanho
            out.write(ByteBuffer.allocate(4).putInt(data.length).array());
            out.write(data);
            out.flush();
            totalSent += data.length;

            // ecoa
            int len = in.read(buffer);
            if (len > 0) totalReceived += len;

            logAndPrint(csv, totalSent, totalReceived);
            Thread.sleep(50);
        }
    }

    private static void sendBursts(OutputStream out, InputStream in, PrintWriter csv, byte[] buffer,
                                   long totalSent, long totalReceived) throws Exception {
        int[] burstSizes = {1024, 8192, 32768, 65536, 131072, 262144, 524288}; // 1KB -> 512KB

        for (int size : burstSizes) {
            byte[] burst = new byte[size];
            for (int i = 0; i < size; i++) burst[i] = (byte) ('A' + (i % 26));

            int chunkSize = Math.min(128 * 1024, size);
            int chunks = (size + chunkSize - 1) / chunkSize;

            for (int rep = 0; rep < 4; rep++) { // repete burst
                for (int c = 0; c < chunks; c++) {
                    int offset = c * chunkSize;
                    int len = Math.min(chunkSize, size - offset);

                    out.write(ByteBuffer.allocate(4).putInt(len).array());
                    out.write(burst, offset, len);
                    out.flush();
                    totalSent += len;

                    int rcv = in.read(buffer);
                    if (rcv > 0) totalReceived += rcv;

                    logAndPrint(csv, totalSent, totalReceived);
                    Thread.sleep(3);
                }
            }

            Thread.sleep(200); // pausa entre bursts
        }
    }

    private static void interactiveChat(OutputStream out, InputStream in, PrintWriter csv, byte[] buffer,
                                        long totalSent, long totalReceived, Scanner sc) throws Exception {
        System.out.println("[CLIENT] Chat iniciado. Digite 'sair' para encerrar.");
        while (true) {
            System.out.print("Você: ");
            String msg = sc.nextLine();
            if (msg.equalsIgnoreCase("sair")) break;

            byte[] data = msg.getBytes();
            out.write(ByteBuffer.allocate(4).putInt(data.length).array());
            out.write(data);
            out.flush();
            totalSent += data.length;

            int len = in.read(buffer);
            if (len > 0) totalReceived += len;

            logAndPrint(csv, totalSent, totalReceived);
        }
    }

    private static void logAndPrint(PrintWriter csv, long totalSent, long totalReceived) {
        long now = System.currentTimeMillis();
        long throughput = (totalSent + totalReceived) / 1000; // simplificado
        long rtt = 0; // simplificado sem ping-pong
        double rttVar = 0;

        csv.printf("%d,%d,%d,%d,%.0f,%d,%s%n",
                now, totalSent, totalReceived, rtt, rttVar, throughput, "OK");
        csv.flush();

        System.out.println("[METRICS] C->S=" + totalSent +
                " | S->C=" + totalReceived +
                " | Throughput=" + throughput + " B/s");
    }

    private static String resolve(String hostname) {
        try {
            java.net.InetAddress.getByName(hostname);
            System.out.println("[CLIENT] Usando hostname Docker: " + hostname);
            return hostname;
        } catch (Exception e) {
            System.out.println("[CLIENT] Fallback -> localhost");
            return "localhost";
        }
    }
}
