package guessarena;

/** Thrown when a player's input is not a legal guess. */
public class InvalidGuessException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidGuessException(String message) {
        super(message);
    }
}
