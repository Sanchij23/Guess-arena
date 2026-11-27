package guessarena;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Network side of a player. Has no GUI code, so it is easy to test. */
public class GameClient {
    private final Connection connection;
    private final ScheduledExecutorService heartbeat;
    private volatile boolean closedByUser;

    /** Connects and sends JOIN. Blocks, so never call it on the GUI thread. */
    public GameClient(String host, int port, String name) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), 5000);
            connection = new Connection(socket);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
        connection.send(new Message(Message.Type.JOIN, name));
        heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleAtFixedRate(
                () -> connection.send(new Message(Message.Type.PING, "")), 3, 3, TimeUnit.SECONDS);
    }

    /** Starts the receiving thread. */
    public void listen(Consumer<Message> onMessage, Runnable onDisconnect) {
        Thread reader = new Thread(() -> {
            try {
                while (true) onMessage.accept(connection.receive());
            } catch (IOException e) {
                heartbeat.shutdownNow();
                connection.close();
                if (!closedByUser) onDisconnect.run();
            }
        }, "reader");
        reader.setDaemon(true);
        reader.start();
    }

    public void guess(String text) {
        connection.send(new Message(Message.Type.GUESS, text));
    }

    public void close() {
        closedByUser = true;
        heartbeat.shutdownNow();
        connection.close();
    }
}
