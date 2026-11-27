package guessarena;

public class WordPuzzle extends Puzzle {
    private final String word;
    private final String hint;

    public WordPuzzle(String word, String hint) {
        this.word = word.toUpperCase(java.util.Locale.ROOT);
        this.hint = hint;
    }

    /** The first letter appears at 2/3 time left, the last letter at 1/3. */
    @Override
    public String prompt(double fractionTimeLeft) {
        StringBuilder shown = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            boolean reveal = (i == 0 && fractionTimeLeft <= 0.67)
                    || (i == word.length() - 1 && fractionTimeLeft <= 0.34);
            shown.append(reveal ? word.charAt(i) : '_').append(' ');
        }
        return shown.toString().trim() + "   (Hint: " + hint + ")";
    }

    @Override
    public Result check(String guess) throws InvalidGuessException {
        String g = guess.trim().toUpperCase(java.util.Locale.ROOT);
        if (!g.matches("[A-Z]+")) {
            throw new InvalidGuessException("Use letters A-Z only.");
        }
        if (g.length() != word.length()) {
            throw new InvalidGuessException("The word has " + word.length() + " letters.");
        }
        if (g.equals(word)) return new Result(true, g + " is correct!");
        int match = 0;
        for (int i = 0; i < word.length(); i++) {
            if (g.charAt(i) == word.charAt(i)) match++;
        }
        return new Result(false, g + " is wrong. Letters in the right place: " + match);
    }

    @Override
    public String answer() {
        return word;
    }
}
