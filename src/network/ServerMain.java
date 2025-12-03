package network;

public class ServerMain {
    public static void main(String[] args) {
        new ServerTCP(9000).run();
    }
}
