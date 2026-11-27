package guessarena;

import java.io.Serializable;
import java.util.ArrayList;

/** The single object that travels between server and client (serialization). */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        JOIN, GUESS, PING,                       // client -> server
        WELCOME, ERROR, INFO, PLAYERS,           // server -> client
        ROUND_START, TICK, FEEDBACK, ROUND_END, MATCH_END
    }

    public final Type type;
    public final String text;
    public final int value;                      // seconds left / round time
    public final ArrayList<PlayerInfo> players;  // ranking, best first

    public Message(Type type, String text, int value, ArrayList<PlayerInfo> players) {
        this.type = type;
        this.text = text;
        this.value = value;
        this.players = players;
    }

    public Message(Type type, String text) {
        this(type, text, 0, null);
    }
}
