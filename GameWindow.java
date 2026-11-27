package guessarena;

import java.awt.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** The Swing window: a home screen and a game screen (lobby + play). */
public class GameWindow extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final Color DARK = new Color(0x1B1446);
    private static final Color PURPLE = new Color(0x5B3FD6);
    private static final Color GOLD = new Color(0xFFC83D);
    private static final Color LIGHT = new Color(0xF4F2FC);

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    private final JTextField nameField = new JTextField();
    private final JTextField ipField = new JTextField("localhost");
    private final JTextField portField = new JTextField("5050");
    private final JButton hostButton = button("Host Game");
    private final JButton joinButton = button("Join Game");

    private final JLabel roundLabel = new JLabel();
    private final JLabel timerLabel = new JLabel();
    private final JLabel promptLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel addressLabel = new JLabel();
    private final JProgressBar timeBar = new JProgressBar();
    private final JTextArea feed = new JTextArea();
    private final JTextField guessField = new JTextField();
    private final JButton guessButton = button("Guess");
    private final JButton startButton = button("Start Match");
    private final JComboBox<String> modeBox = new JComboBox<>(new String[] {"Number", "Word"});
    private final JSpinner roundsSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));
    private final JSpinner secondsSpinner = new JSpinner(new SpinnerNumberModel(45, 10, 180, 5));
    private final JPanel hostPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
    private final DefaultTableModel tableModel =
            new DefaultTableModel(new String[] {"Rank", "Player", "Score", "Solved"}, 0) {
                private static final long serialVersionUID = 1L;

                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    // These two fields are only used on the Swing event thread.
    private GameServer server;
    private GameClient client;
    private boolean joined;

    public GameWindow() {
        super("Guess Arena");
        setIconImages(Logo.icons());
        root.add(homeScreen(), "home");
        root.add(gameScreen(), "game");
        add(root);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(860, 600));
        setLocationRelativeTo(null);
    }

    private static JButton button(String text) {
        JButton b = new JButton(text);
        b.setBackground(PURPLE);
        b.setForeground(Color.WHITE);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        b.setFont(b.getFont().deriveFont(Font.BOLD, 14f));
        return b;
    }

    private JPanel homeScreen() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(DARK);
        JPanel card = new JPanel(new GridLayout(0, 1, 0, 6));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createEmptyBorder(18, 36, 18, 36));

        card.add(new JLabel("Your name"));
        card.add(nameField);
        card.add(new JLabel("Host IP address (only needed to join)"));
        card.add(ipField);
        card.add(new JLabel("Port"));
        card.add(portField);
        card.add(hostButton);
        card.add(joinButton);
        JButton fame = new JButton("Hall of Fame");
        card.add(fame);

        JLabel title = new JLabel("GUESS ARENA", new ImageIcon(Logo.create(96)), SwingConstants.CENTER);
        title.setHorizontalTextPosition(SwingConstants.CENTER);
        title.setVerticalTextPosition(SwingConstants.BOTTOM);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 30f));
        title.setForeground(GOLD);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.insets = new Insets(8, 0, 8, 0);
        page.add(title, c);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.ipadx = 100;
        page.add(card, c);

        hostButton.addActionListener(e -> connect(true));
        joinButton.addActionListener(e -> connect(false));
        fame.addActionListener(e -> JOptionPane.showMessageDialog(this,
                new HallOfFame().describe(), "Hall of Fame (this computer)", JOptionPane.PLAIN_MESSAGE));
        return page;
    }

    private JPanel gameScreen() {
        JPanel page = new JPanel(new BorderLayout(10, 10));
        page.setBackground(LIGHT);
        page.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setBackground(DARK);
        header.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        for (JLabel l : new JLabel[] {roundLabel, timerLabel, promptLabel, addressLabel}) {
            l.setForeground(Color.WHITE);
        }
        roundLabel.setFont(roundLabel.getFont().deriveFont(Font.BOLD, 18f));
        timerLabel.setFont(timerLabel.getFont().deriveFont(Font.BOLD, 18f));
        timerLabel.setForeground(GOLD);
        promptLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        promptLabel.setPreferredSize(new Dimension(100, 56));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(roundLabel, BorderLayout.WEST);
        top.add(timerLabel, BorderLayout.EAST);
        timeBar.setForeground(GOLD);
        header.add(top, BorderLayout.NORTH);
        header.add(promptLabel, BorderLayout.CENTER);
        header.add(timeBar, BorderLayout.SOUTH);

        feed.setEditable(false);
        feed.setLineWrap(true);
        feed.setWrapStyleWord(true);
        feed.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        JScrollPane feedPane = new JScrollPane(feed);
        feedPane.setBorder(BorderFactory.createTitledBorder("Game feed"));

        JTable table = new JTable(tableModel);
        table.setRowHeight(26);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.setFocusable(false);
        JScrollPane tablePane = new JScrollPane(table);
        tablePane.setBorder(BorderFactory.createTitledBorder("Players and ranking"));
        tablePane.setPreferredSize(new Dimension(370, 100));

        JPanel guessRow = new JPanel(new BorderLayout(8, 0));
        guessRow.setOpaque(false);
        guessField.setFont(guessField.getFont().deriveFont(18f));
        guessRow.add(guessField, BorderLayout.CENTER);
        guessRow.add(guessButton, BorderLayout.EAST);

        hostPanel.setOpaque(false);
        hostPanel.add(new JLabel("Mode"));
        hostPanel.add(modeBox);
        hostPanel.add(new JLabel("Rounds"));
        hostPanel.add(roundsSpinner);
        hostPanel.add(new JLabel("Seconds"));
        hostPanel.add(secondsSpinner);
        hostPanel.add(startButton);

        JButton leaveButton = new JButton("Leave Game");
        JPanel footer = new JPanel(new BorderLayout(8, 6));
        footer.setOpaque(false);
        footer.add(guessRow, BorderLayout.NORTH);
        footer.add(hostPanel, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        addressLabel.setForeground(DARK);
        bottom.add(addressLabel, BorderLayout.WEST);
        bottom.add(leaveButton, BorderLayout.EAST);
        footer.add(bottom, BorderLayout.SOUTH);

        page.add(header, BorderLayout.NORTH);
        page.add(feedPane, BorderLayout.CENTER);
        page.add(tablePane, BorderLayout.EAST);
        page.add(footer, BorderLayout.SOUTH);

        guessButton.addActionListener(e -> submitGuess());
        guessField.addActionListener(e -> submitGuess());
        startButton.addActionListener(e -> startMatch());
        leaveButton.addActionListener(e -> {
            String warning = server != null ? "You are the host. Leaving ends the game for everyone. Leave?"
                    : "Leave this game?";
            if (JOptionPane.showConfirmDialog(this, warning, "Leave",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                goHome();
            }
        });
        return page;
    }

    private void connect(boolean hosting) {
        String name = nameField.getText().trim();
        String host = hosting ? "localhost" : ipField.getText().trim();
        if (!name.matches("[A-Za-z0-9_ ]{2,15}")) {
            error("Name must be 2-15 letters, digits, spaces or _.");
            return;
        }
        if (host.isEmpty()) {
            error("Enter the host's IP address.");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            port = -1;
        }
        if (port < 1024 || port > 65535) {
            error("Port must be a number from 1024 to 65535.");
            return;
        }
        setConnecting(true);
        final int p = port;
        // SwingWorker keeps the window responsive while connecting.
        new SwingWorker<GameClient, Void>() {
            private GameServer newServer;

            @Override
            protected GameClient doInBackground() throws IOException {
                if (hosting) newServer = new GameServer(p, new HallOfFame());
                try {
                    return new GameClient(host, p, name);
                } catch (IOException e) {
                    if (newServer != null) newServer.close();
                    throw e;
                }
            }

            @Override
            protected void done() {
                setConnecting(false);
                try {
                    GameClient c = get();
                    server = newServer;
                    openGame(c, p);
                } catch (Exception e) {
                    error((hosting ? "Could not host. The port may already be in use."
                            : "Could not connect. Check the IP, port, network and firewall.")
                            + "\n\nDetails: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                }
            }
        }.execute();
    }

    private void setConnecting(boolean busy) {
        hostButton.setEnabled(!busy);
        joinButton.setEnabled(!busy);
        setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void openGame(GameClient newClient, int port) {
        client = newClient;
        joined = false;
        feed.setText("");
        tableModel.setRowCount(0);
        showLobby();
        hostPanel.setVisible(server != null);
        addressLabel.setText(server != null
                ? "Share with players:  IP " + String.join(" or ", localAddresses()) + "   Port " + port
                : "Waiting for the host to start the match.");
        // Network events arrive on another thread, so hand them to the Swing thread.
        newClient.listen(
                m -> SwingUtilities.invokeLater(() -> {
                    if (client == newClient) handle(m);
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    if (client == newClient) {
                        goHome();
                        error("Connection lost. The host may have closed the game.");
                    }
                }));
    }

    private void handle(Message m) {
        if (m.players != null) updateTable(m.players);
        switch (m.type) {
            case WELCOME:
                joined = true;
                setTitle("Guess Arena - " + m.text);
                cards.show(root, "game");
                log("Welcome to the lobby, " + m.text + "!");
                break;
            case ERROR:
                goHome();
                error(m.text);
                break;
            case INFO:
                log(m.text);
                break;
            case FEEDBACK:
                log("> " + m.text);
                break;
            case ROUND_START:
                roundLabel.setText(m.text);
                timeBar.setMaximum(Math.max(1, m.value));
                log("----- " + m.text + " -----");
                setPlaying(true);
                break;
            case TICK:
                promptLabel.setText(m.text);
                timerLabel.setText("Time: " + m.value + "s");
                timeBar.setValue(m.value);
                break;
            case ROUND_END:
                setPlaying(false);
                promptLabel.setText("Round over");
                log(m.text);
                break;
            case MATCH_END:
                showLobby();
                promptLabel.setText("Match finished");
                log(m.text);
                announce(m);
                break;
            default:
                break;                            // PING needs no action
        }
    }

    private void announce(Message m) {
        StringBuilder s = new StringBuilder(m.text).append("\n\nFinal ranking\n");
        int rank = 0;
        for (int i = 0; i < m.players.size(); i++) {
            if (i == 0 || m.players.get(i).score != m.players.get(i - 1).score) rank = i + 1;
            s.append(rank).append(". ").append(m.players.get(i).name)
             .append(" - ").append(m.players.get(i).score).append('\n');
        }
        // Non-blocking dialog: the game keeps updating behind it.
        JDialog d = new JOptionPane(s.toString(), JOptionPane.PLAIN_MESSAGE).createDialog(this, "Winner Announcement");
        d.setModal(false);
        d.setVisible(true);
    }

    private void updateTable(List<PlayerInfo> list) {
        tableModel.setRowCount(0);
        int rank = 0;
        for (int i = 0; i < list.size(); i++) {
            PlayerInfo p = list.get(i);
            if (i == 0 || p.score != list.get(i - 1).score) rank = i + 1;   // equal scores share a rank
            tableModel.addRow(new Object[] {rank, p.name, p.score, p.solved ? "Yes" : ""});
        }
    }

    private void showLobby() {
        roundLabel.setText("Game Lobby");
        timerLabel.setText("");
        timeBar.setValue(0);
        promptLabel.setText("Waiting for players (minimum 2)");
        setPlaying(false);
        startButton.setEnabled(true);
    }

    private void setPlaying(boolean playing) {
        guessField.setEnabled(playing);
        guessButton.setEnabled(playing);
        guessField.setText("");
        if (playing) {
            startButton.setEnabled(false);
            guessField.requestFocusInWindow();
        }
    }

    private void submitGuess() {
        String text = guessField.getText().trim();
        if (client == null || !joined || !guessField.isEnabled()) return;
        if (text.isEmpty()) {
            log("> Type a guess first.");
            return;
        }
        client.guess(text);
        guessField.setText("");
    }

    private void startMatch() {
        if (server == null) return;
        try {
            roundsSpinner.commitEdit();
            secondsSpinner.commitEdit();
        } catch (java.text.ParseException e) {
            error("Rounds and seconds must be valid numbers within range.");
            return;
        }
        String problem = server.startMatch(modeBox.getSelectedIndex() == 1,
                (Integer) roundsSpinner.getValue(), (Integer) secondsSpinner.getValue());
        if (problem != null) error(problem);
    }

    private void goHome() {
        if (client != null) client.close();
        if (server != null) server.close();
        client = null;
        server = null;
        setTitle("Guess Arena");
        cards.show(root, "home");
    }

    @Override
    public void dispose() {
        goHome();
        super.dispose();
    }

    private void log(String line) {
        feed.append(line + "\n");
        feed.setCaretPosition(feed.getDocument().getLength());
    }

    private void error(String text) {
        JOptionPane.showMessageDialog(this, text, "Guess Arena", JOptionPane.WARNING_MESSAGE);
    }

    private static List<String> localAddresses() {
        List<String> result = new ArrayList<>();
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback()) continue;
                for (InetAddress a : Collections.list(n.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) result.add(a.getHostAddress());
                }
            }
        } catch (SocketException e) {
            System.err.println("Could not read IP address: " + e.getMessage());
        }
        if (result.isEmpty()) result.add("(run ipconfig to find it)");
        return result;
    }
}
