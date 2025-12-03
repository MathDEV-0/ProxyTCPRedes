package network;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Scanner;

public class ClientTCP {

    public void start(String host, int port) {
        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream();
             InputStream in = socket.getInputStream();
             Scanner sc = new Scanner(System.in)) {

            System.out.println("[CLIENT] Conectado ao proxy em " + host + ":" + port);

            while (true) {
                System.out.print("> ");
                String msg = sc.nextLine();

                out.write(msg.getBytes());
                out.flush();

                byte[] buffer = new byte[8192];
                int len = in.read(buffer);

                System.out.println("[CLIENT] Resposta: " + new String(buffer, 0, len));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
