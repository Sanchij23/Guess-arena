# Guess Arena - Multiplayer Number/Word Game

## Build the Windows installer (on a Windows PC)
1. Install JDK 17 or newer. Check with `javac -version` and `jpackage --version`.
2. Install WiX Toolset 3.14 (jpackage needs it to make .exe files). Add its `bin`
   folder to PATH, for example `C:\Program Files (x86)\WiX Toolset v3.14\bin`.
   Newer WiX versions work only with newer JDKs, so 3.14 is the safe choice.
3. Double-click `build-installer.bat`.
4. Run `installer\GuessArena-1.0.0.exe`, then start Guess Arena from the Start menu.

To run without installing, after the script has created the JAR:
`java -jar dist\GuessArena.jar`

## How to play
1. One player enters a name and clicks **Host Game**.
2. Others enter a name, the host's IP and port, and click **Join Game**.
3. With 2 to 8 players in the lobby, the host picks mode, rounds and seconds, then starts.
- Number mode: find a number from 1 to 100 using higher/lower hints.
- Word mode: guess the word from its hint. The first and last letters appear over time.
- Score: 50 + up to 50 for speed + 30 (first solver) or 15 (second).
- Equal scores share a rank. Scores reset when a new match starts.

## Network setup
- Two PCs: connect both to the same Wi-Fi or hotspot. Use the host's IPv4 address
  (shown in the game, or from `ipconfig`). If several are shown, use the Wi-Fi one.
- Allow the app through Windows Firewall on the host (Private networks).
  Some college Wi-Fi blocks PC-to-PC traffic; a mobile hotspot usually avoids this.
- One PC: open the app twice. Host in one, join `localhost` in the other.

## Java concepts used (for the review)
| Concept | Where |
|---|---|
| Abstraction, inheritance, polymorphism | `Puzzle`, `NumberPuzzle`, `WordPuzzle` |
| Encapsulation | `GameServer` keeps all game state private |
| Exception handling (including custom) | `InvalidGuessException`, `Connection`, `GameWindow.connect` |
| Collections | `LinkedHashMap` of players, `ArrayList` ranking, `ArrayDeque` word deck, `HashMap` wins |
| Multithreading | accept loop, one thread per client, writer threads, timer, `SwingWorker` |
| Synchronization | `synchronized` methods in `GameServer` |
| Socket programming | `GameServer`, `GameClient`, `Connection` |
| Serialization | `Message`, `PlayerInfo` over object streams |
| File handling | `WordBank` reads `words.txt`; `HallOfFame` saves wins |

## Flow to explain
Client sends JOIN -> server replies WELCOME and the player list -> host starts ->
server sends ROUND_START and a TICK every second -> client sends GUESS -> server
replies FEEDBACK and updates the ranking -> ROUND_END -> after the last round, MATCH_END.

## Likely viva questions
- **Why is the answer kept only on the server?** So clients cannot read it or fake scores.
- **Why synchronized?** Client threads and the timer change the same game state.
- **Why SwingUtilities.invokeLater?** Swing components must be updated on the event thread.
- **Why a writer thread per connection?** A slow player must not delay the others.
- **How is a dropped network detected?** Both sides send PING; 15 seconds of silence closes the connection.
- **Why an ObjectInputFilter?** Deserializing unknown classes from a network is unsafe.
- **Limitations:** traffic is not encrypted, so use it on a trusted local network.
  The game ends if the host closes the app.
