package guessarena;

/** One round's secret. Subclasses show inheritance and polymorphism. */
public abstract class Puzzle {
    /** Result of checking one guess. */
    public static final class Result {
        public final boolean correct;
        public final String feedback;

        Result(boolean correct, String feedback) {
            this.correct = correct;
            this.feedback = feedback;
        }
    }

    /** Question shown to players; may change as time runs out. */
    public abstract String prompt(double fractionTimeLeft);

    /** @throws InvalidGuessException if the input is not a legal guess */
    public abstract Result check(String guess) throws InvalidGuessException;

    public abstract String answer();
}
