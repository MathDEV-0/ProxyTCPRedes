package metrics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class PCAPWriter {

    private final FileOutputStream out;
    private volatile boolean open = true;

    // PCAP Global Header (little-endian, microsecond precision)
    private static final byte[] GLOBAL_HEADER = {
            (byte)0xd4, (byte)0xc3, (byte)0xb2, (byte)0xa1, // magic
            0x02, 0x00, // version major
            0x04, 0x00, // version minor
            0x00, 0x00, 0x00, 0x00, // thiszone
            0x00, 0x00, 0x00, 0x00, // sigfigs
            (byte)0xff, (byte)0xff, 0x00, 0x00, // snaplen
            0x01, 0x00, 0x00, 0x00  // network = LINKTYPE_ETHERNET (1) -> safer than raw for Wireshark
    };

    public PCAPWriter(String path) throws IOException {
        File f = new File(path);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        this.out = new FileOutputStream(f);
        out.write(GLOBAL_HEADER);
        out.flush();
    }

    /**
     * Escreve um pacote. 'data' pode ser maior que 'len' => apenas os primeiros 'len' bytes
     */
    public synchronized void writePacket(byte[] data, int len) {
        if (!open) return;
        if (len <= 0) return;

        long ts = System.currentTimeMillis();
        int sec = (int) (ts / 1000);
        int usec = (int) ((ts % 1000) * 1000);

        try {
            // per-packet header (little-endian)
            writeIntLE(sec);
            writeIntLE(usec);
            writeIntLE(len);
            writeIntLE(len);

            // payload (apenas len bytes)
            out.write(data, 0, len);
            out.flush();
        } catch (IOException e) {
            System.err.println("[PCAP] erro ao escrever pacote: " + e.getMessage());
        }
    }

    private void writeIntLE(int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    public synchronized void close() {
        open = false;
        try {
            out.close();
        } catch (IOException ignored) {}
    }
}
