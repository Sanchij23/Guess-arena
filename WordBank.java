package guessarena;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Loads "word,hint" lines from words.txt (file handling). */
public class WordBank {
    private final List<String[]> words = new ArrayList<>();
    private final Deque<String[]> deck = new ArrayDeque<>();

    public WordBank() {
        try (InputStream in = WordBank.class.getResourceAsStream("/words.txt")) {
            if (in != null) load(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            System.err.println("Could not read words.txt: " + e.getMessage());
        }
        if (words.isEmpty()) {                    // fallback so the game still works
            words.add(new String[] {"java", "programming language"});
        }
    }

    private void load(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",", 2);
            if (parts.length == 2 && parts[0].trim().matches("[A-Za-z]+")) {
                words.add(new String[] {parts[0].trim(), parts[1].trim()});
            }
        }
    }

    /** Words do not repeat until all of them have been used. */
    public WordPuzzle next() {
        if (deck.isEmpty()) {
            List<String[]> copy = new ArrayList<>(words);
            Collections.shuffle(copy);
            deck.addAll(copy);
        }
        String[] w = deck.pop();
        return new WordPuzzle(w[0], w[1]);
    }
}
