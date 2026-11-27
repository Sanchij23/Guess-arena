package guessarena;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Wraps a socket. A separate writer thread sends queued messages, so one slow
 * network connection can never freeze the game or the window.
 */
public class Connection implements Closeable {
    /** If nothing arrives for this long, the other side is treated as lost. */
    private static final int READ_TIMEOUT_MS = 15000;

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private final BlockingQueue<Message> outbox = new LinkedBlockingQueue<>(500);
    private final Thread writer;
    private volatile boolean open = true;

    public Connection(Socket socket) throws IOException {
        this.socket = socket;
        socket.setSoTimeout(READ_TIMEOUT_MS);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();                              // send header first to avoid deadlock
        in = new ObjectInputStream(socket.getInputStream());
        // Security: only accept the classes our protocol really uses.
        in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                "maxbytes=1000000;maxarray=1000;maxdepth=10;"
                + "guessarena.*;java.util.ArrayList;java.lang.Enum;java.lang.Object;!*"));
        writer = new Thread(this::writeLoop, "writer");
        writer.setDaemon(true);
        writer.start();
    }

    private void writeLoop() {
        try {
            while (open) {
                Message m = outbox.take();
                out.writeObject(m);
                out.reset();                      // do not cache old objects
                out.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            close();
        }
    }

    public void send(Message m) {
        if (open && !outbox.offer(m)) {
            close();                              // peer has stopped reading
        }
    }

    public Message receive() throws IOException {
        try {
            Object o = in.readObject();
            if (o instanceof Message && ((Message) o).type != null) {
                return (Message) o;
            }
            throw new IOException("Unknown data received");
        } catch (ClassNotFoundException e) {
            throw new IOException("Incompatible version", e);
        }
    }

    /** Waits briefly so that a final message (such as an error) gets delivered. */
    public void closeAfterSending() {
        try {
            for (int i = 0; i < 20 && !outbox.isEmpty(); i++) Thread.sleep(25);
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        close();
    }

    @Override
    public void close() {
        open = false;
        writer.interrupt();
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing useful can be done while closing
        }
    }
}
