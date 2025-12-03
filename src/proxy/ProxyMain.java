package proxy;

public class ProxyMain {
    public static void main(String[] args) {
        String targetHost = resolve("tcp-server");
        new Thread(new ProxyTCP(8000, targetHost, 9000)).start();
    }

    private static String resolve(String hostname) {
        try {
            java.net.InetAddress.getByName(hostname);
            System.out.println("[PROXY] Usando hostname do Docker: " + hostname);
            return hostname;
        } catch (Exception e) {
            System.out.println("[PROXY] Host " + hostname + " indisponível. Usando localhost.");
            return "localhost";
        }
    }
}
