package guessarena;

import java.io.Serializable;

/** Read-only snapshot of a player, sent to clients for the ranking table. */
public class PlayerInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String name;
    public final int score;
    public final boolean solved;

    public PlayerInfo(String name, int score, boolean solved) {
        this.name = name;
        this.score = score;
        this.solved = solved;
    }
}
