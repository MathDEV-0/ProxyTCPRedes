package application;

import proxy.ProxyTCP;
import testenv.ClientSimulator;
import testenv.EchoServer;

public class Main {

    public static void main(String[] args) throws Exception {

        // 1) servidor real (echo)
        new Thread(new EchoServer(9000)).start();

        Thread.sleep(300);

        // 2) proxy
        new Thread(new ProxyTCP(8000, "localhost", 9000)).start();

        Thread.sleep(300);

        // 3) cliente via proxy
        new Thread(new ClientSimulator(8000)).start();
    }
}
