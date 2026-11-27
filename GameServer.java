package guessarena;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Owns all game state. Clients only send guesses; the server decides everything.
 * Methods that touch game state are synchronized because many threads call them.
 */
public class GameServer implements Closeable {
    public static final int MAX_PLAYERS = 8;
    private static final long GUESS_GAP_MS = 800;
    private static final int BREAK_SECONDS = 5;

    private enum State { LOBBY, ROUND, BREAK }

    /** Server-side data of one connected player. */
    private static final class Player {
        final String name;
        final Connection connection;
        int score;
        boolean solved;
        long lastGuess;

        Player(String name, Connection connection) {
            this.name = name;
            this.connection = connection;
        }
    }

    private final ServerSocket serverSocket;
    private final ExecutorService pool = Executors.newCachedThreadPool(daemon("client"));
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(daemon("clock"));
    private final Map<String, Player> players = new LinkedHashMap<>();
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    private final WordBank wordBank = new WordBank();
    private final HallOfFame hallOfFame;
    private final Random random = new Random();

    private State state = State.LOBBY;
    private boolean wordMode;
    private int totalRounds, roundSeconds, round, timeLeft, solvedCount;
    private Puzzle puzzle;

    public GameServer(int port, HallOfFame hallOfFame) throws IOException {
        this.hallOfFame = hallOfFame;
        serverSocket = new ServerSocket(port);    // fails if the port is busy
        pool.execute(this::acceptLoop);
        clock.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                pool.execute(() -> serve(socket));
            } catch (IOException | RejectedExecutionException e) {
                if (!serverSocket.isClosed()) System.err.println("Accept failed: " + e.getMessage());
            }
        }
    }

    /** Runs on its own thread for each client. */
    private void serve(Socket socket) {
        Player player = null;
        Connection connection = null;
        try {
            connection = new Connection(socket);
            connections.add(connection);
            Message first = connection.receive();
            if (first.type != Message.Type.JOIN) return;
            player = join(first.text, connection);
            if (player == null) {
                connection.closeAfterSending();
                return;
            }
            while (true) {
                Message m = connection.receive();
                if (m.type == Message.Type.GUESS) handleGuess(player, m.text);
            }
        } catch (IOException e) {
            // normal when a player leaves or the network drops
        } finally {
            if (player != null) leave(player);
            if (connection != null) {
                connections.remove(connection);
                connection.close();
            } else {
                try { socket.close(); } catch (IOException ignored) { }
            }
        }
    }

    private synchronized Player join(String rawName, Connection connection) {
        String name = rawName == null ? "" : rawName.trim();
        String problem = null;
        if (!name.matches("[A-Za-z0-9_ ]{2,15}")) {
            problem = "Name must be 2-15 letters, digits, spaces or _.";
        } else if (players.containsKey(name.toLowerCase(Locale.ROOT))) {
            problem = "That name is already taken.";
        } else if (players.size() >= MAX_PLAYERS) {
            problem = "The game is full (" + MAX_PLAYERS + " players).";
        }
        if (problem != null) {
            connection.send(new Message(Message.Type.ERROR, problem));
            return null;
        }
        Player p = new Player(name, connection);
        players.put(name.toLowerCase(Locale.ROOT), p);
        connection.send(new Message(Message.Type.WELCOME, name));
        broadcast(new Message(Message.Type.INFO, name + " joined the game."));
        sendRanking();
        if (state == State.ROUND) {
            connection.send(new Message(Message.Type.ROUND_START, roundTitle(), timeLeft, ranking()));
            sendTick(p);
        }
        return p;
    }

    private synchronized void leave(Player p) {
        if (!players.remove(p.name.toLowerCase(Locale.ROOT), p)) return;
        if (p.solved) solvedCount--;
        broadcast(new Message(Message.Type.INFO, p.name + " left the game."));
        sendRanking();
        if (state != State.LOBBY && players.isEmpty()) {
            state = State.LOBBY;
        } else if (state == State.ROUND && solvedCount == players.size()) {
            endRound();
        }
    }

    /** Called by the host's window. Returns null on success, else the reason. */
    public synchronized String startMatch(boolean words, int rounds, int seconds) {
        if (state != State.LOBBY) return "A match is already running.";
        if (players.size() < 2) return "At least 2 players are needed.";
        if (rounds < 1 || rounds > 20 || seconds < 10 || seconds > 180) {
            return "Choose 1-20 rounds and 10-180 seconds.";
        }
        wordMode = words;
        totalRounds = rounds;
        roundSeconds = seconds;
        round = 0;
        for (Player p : players.values()) p.score = 0;
        startRound();
        return null;
    }

    private void startRound() {
        round++;
        solvedCount = 0;
        for (Player p : players.values()) p.solved = false;
        puzzle = wordMode ? wordBank.next() : new NumberPuzzle(random);
        timeLeft = roundSeconds;
        state = State.ROUND;
        broadcast(new Message(Message.Type.ROUND_START, roundTitle(), timeLeft, ranking()));
        for (Player p : players.values()) sendTick(p);
    }

    private String roundTitle() {
        return "Round " + round + " of " + totalRounds;
    }

    private void sendTick(Player p) {
        p.connection.send(new Message(Message.Type.TICK,
                puzzle.prompt((double) timeLeft / roundSeconds), timeLeft, null));
    }

    /** Called once every second by the timer thread. */
    private synchronized void tick() {
        try {
            broadcast(new Message(Message.Type.PING, ""));   // keeps connections alive
            if (state == State.ROUND) {
                if (--timeLeft <= 0) endRound();
                else for (Player p : players.values()) sendTick(p);
            } else if (state == State.BREAK && --timeLeft <= 0) {
                if (round >= totalRounds || players.size() < 2) endMatch();
                else startRound();
            }
        } catch (RuntimeException e) {
            e.printStackTrace();                  // an exception must not stop the timer
        }
    }

    private synchronized void handleGuess(Player p, String guess) {
        if (state != State.ROUND) {
            reply(p, "No round is running right now.");
            return;
        }
        if (p.solved) {
            reply(p, "You already solved this round.");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - p.lastGuess < GUESS_GAP_MS) {
            reply(p, "Too fast! Wait a moment.");
            return;
        }
        if (guess == null || guess.length() > 30) {
            reply(p, "That guess is too long.");
            return;
        }
        try {
            Puzzle.Result result = puzzle.check(guess);
            p.lastGuess = now;
            reply(p, result.feedback);
            if (result.correct) {
                int points = points(timeLeft, roundSeconds, solvedCount);
                p.score += points;
                p.solved = true;
                solvedCount++;
                broadcast(new Message(Message.Type.INFO, p.name + " solved it! +" + points));
                sendRanking();
                if (solvedCount == players.size()) endRound();
            }
        } catch (InvalidGuessException e) {
            reply(p, e.getMessage());
        }
    }

    /** 50 base + up to 50 for speed + 30 for first solver, 15 for second. */
    static int points(int timeLeft, int total, int solvedBefore) {
        int bonus = solvedBefore == 0 ? 30 : solvedBefore == 1 ? 15 : 0;
        return 50 + 50 * timeLeft / total + bonus;
    }

    private void endRound() {
        state = State.BREAK;
        timeLeft = BREAK_SECONDS;
        String next = round >= totalRounds || players.size() < 2
                ? "Final results coming..." : "Next round in " + BREAK_SECONDS + " seconds.";
        broadcast(new Message(Message.Type.ROUND_END,
                "The answer was " + puzzle.answer() + ". " + next, 0, ranking()));
    }

    private void endMatch() {
        state = State.LOBBY;
        ArrayList<PlayerInfo> ranks = ranking();
        List<String> winners = new ArrayList<>();
        for (PlayerInfo i : ranks) {
            if (i.score > 0 && i.score == ranks.get(0).score) winners.add(i.name);
        }
        String text;
        if (winners.isEmpty()) text = "Match over. Nobody scored, so there is no winner.";
        else if (winners.size() == 1) text = "WINNER: " + winners.get(0) + " with " + ranks.get(0).score + " points!";
        else text = "It's a TIE between " + String.join(" and ", winners) + " (" + ranks.get(0).score + " points)!";
        for (String w : winners) {
            try {
                hallOfFame.addWin(w);
            } catch (IOException e) {
                broadcast(new Message(Message.Type.INFO, "Could not save the Hall of Fame."));
            }
        }
        broadcast(new Message(Message.Type.MATCH_END, text, 0, ranks));
    }

    private ArrayList<PlayerInfo> ranking() {
        ArrayList<PlayerInfo> list = new ArrayList<>();
        for (Player p : players.values()) list.add(new PlayerInfo(p.name, p.score, p.solved));
        list.sort((a, b) -> a.score != b.score ? b.score - a.score : a.name.compareToIgnoreCase(b.name));
        return list;
    }

    private void sendRanking() {
        broadcast(new Message(Message.Type.PLAYERS, "", 0, ranking()));
    }

    private void reply(Player p, String text) {
        p.connection.send(new Message(Message.Type.FEEDBACK, text));
    }

    private void broadcast(Message m) {
        for (Player p : players.values()) p.connection.send(m);
    }

    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException ignored) { }
        clock.shutdownNow();
        for (Connection c : connections) c.close();
        pool.shutdownNow();
    }
}
