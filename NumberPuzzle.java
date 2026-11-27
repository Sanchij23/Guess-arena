package guessarena;

import java.util.Random;

public class NumberPuzzle extends Puzzle {
    private final int secret;

    public NumberPuzzle(Random random) {
        secret = random.nextInt(100) + 1;
    }

    @Override
    public String prompt(double fractionTimeLeft) {
        return "Guess the number from 1 to 100";
    }

    @Override
    public Result check(String guess) throws InvalidGuessException {
        int n;
        try {
            n = Integer.parseInt(guess.trim());
        } catch (NumberFormatException e) {
            throw new InvalidGuessException("Please enter a whole number.");
        }
        if (n < 1 || n > 100) {
            throw new InvalidGuessException("The number must be between 1 and 100.");
        }
        if (n == secret) return new Result(true, n + " is correct!");
        return new Result(false, n + (n < secret ? " is too LOW. Go higher." : " is too HIGH. Go lower."));
    }

    @Override
    public String answer() {
        return String.valueOf(secret);
    }
}
