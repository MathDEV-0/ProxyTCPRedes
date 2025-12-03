    // package network;

    // public class ClientMain {
    //     public static void main(String[] args) {
    //         new ClientTCP().start(resolve("tcp-proxy"), 8000);
    //     }

    //     private static String resolve(String hostname) {
    //         try {
    //             java.net.InetAddress.getByName(hostname);
    //             System.out.println("[CLIENT] Usando hostname do Docker: " + hostname);
    //             return hostname;
    //         } catch (Exception e) {
    //             System.out.println("[CLIENT] Host " + hostname + " indisponível. Usando localhost.");
    //             return "localhost";
    //         }
    //     }
    // }

    package network;

public class ClientMain {
    public static void main(String[] args) {
        new Client_Test().start(resolve("tcp-proxy"), 8000);
    }

    private static String resolve(String hostname) {
        try {
            java.net.InetAddress.getByName(hostname);
            System.out.println("[CLIENT] Usando hostname do Docker: " + hostname);
            return hostname;
        } catch (Exception e) {
            System.out.println("[CLIENT] Host " + hostname + " indisponível. Usando localhost.");
            return "localhost";
        }
    }
}