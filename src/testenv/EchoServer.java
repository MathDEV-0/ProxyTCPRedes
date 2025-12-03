package testenv;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class EchoServer implements Runnable {

    private final int port;

    public EchoServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("[ECHO] Servidor escutando na porta " + port);

            while (true) {
                Socket s = ss.accept();
                System.out.println("[ECHO] Cliente conectado");

                new Thread(() -> handle(s)).start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handle(Socket s) {
        try (InputStream in = s.getInputStream();
             OutputStream out = s.getOutputStream()) {

            byte[] buffer = new byte[4096];
            int r;

            while ((r = in.read(buffer)) != -1) {
                out.write(buffer, 0, r);
                out.flush();
            }

        } catch (Exception e) {
            System.out.println("[ECHO] Cliente desconectou");
        }
    }
}
