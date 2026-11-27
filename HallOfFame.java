package guessarena;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** All-time match wins, saved on the host computer using serialization. */
public class HallOfFame {
    private final Path file;
    private HashMap<String, Integer> wins = new HashMap<>();

    public HallOfFame() {
        this(Paths.get(System.getProperty("user.home"), ".guessarena", "halloffame.ser"));
    }

    public HallOfFame(Path file) {
        this.file = file;
        load();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!Files.exists(file)) return;
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
            in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                    "java.util.*;java.util.Map$Entry;java.lang.*;!*"));
            wins = (HashMap<String, Integer>) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            System.err.println("Hall of Fame file is damaged, starting fresh: " + e.getMessage());
        }
    }

    public synchronized void addWin(String name) throws IOException {
        wins.merge(name, 1, Integer::sum);
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(file))) {
            out.writeObject(wins);
        }
    }

    public synchronized String describe() {
        if (wins.isEmpty()) return "No matches have been won on this computer yet.";
        List<Map.Entry<String, Integer>> list = new ArrayList<>(wins.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            s.append(i + 1).append(". ").append(list.get(i).getKey())
             .append("  -  wins: ").append(list.get(i).getValue()).append('\n');
        }
        return s.toString();
    }
}
