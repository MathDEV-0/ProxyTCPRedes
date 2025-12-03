package testenv;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ClientSimulator implements Runnable {

    private final int port;

    public ClientSimulator(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try (Socket s = new Socket("localhost", port)) {
            System.out.println("[CLIENT] Conectado ao proxy");

            OutputStream out = s.getOutputStream();
            InputStream  in  = s.getInputStream();

            byte[] msg = "ping".getBytes();
            byte[] buffer = new byte[4096];

            while (true) {
                out.write(msg);
                out.flush();

                int r = in.read(buffer);
                String resp = new String(buffer, 0, r);

                System.out.println("[CLIENT] recebeu: " + resp);

                Thread.sleep(1000);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
