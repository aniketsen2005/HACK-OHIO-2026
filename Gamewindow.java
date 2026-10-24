import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Point;
import java.util.HashMap;

/**
 * GameWindow.java
 * ----------------------------------------------------------
 * The visual part of the game. It shows:
 *   - a countdown timer (1 hour)
 *   - the current riddle
 *   - a simple 2D "map" with one clickable button per landmark
 *
 * It uses GameLogic.java for the rules/score and
 * FirebaseLeaderboard.java to save the final score.
 *
 * TO RUN: open this file in VSCode and click "Run" above the main method.
 */
public class GameWindow extends JFrame {

    // ---- Game data ----
    private GameLogic logic;                                   // the game brain
    private String teamName;
    private String major;
    private int secondsRemaining = GameLogic.TOTAL_SECONDS;   // starts at 3600
    private boolean gameOver = false;

    // ---- Things we see on screen ----
    private JLabel teamLabel;
    private JLabel timerLabel;
    private JLabel scoreLabel;
    private JTextArea riddleBox;
    private JLabel statusLabel;
    private MapPanel mapPanel;
    private HashMap<String, JButton> landmarkButtons = new HashMap<>();

    // ---- Where each landmark sits on the map (x, y in pixels) ----
    // TEAMMATES: change these numbers to move the buttons around!
    private HashMap<String, Point> landmarkPositions = new HashMap<>();

    // The countdown timer. It "ticks" once per second.
    // (javax.swing.Timer is the beginner-friendly, safe way to do a timer in Swing.)
    private javax.swing.Timer countdownTimer;

    /**
     * Constructor: builds the whole window.
     */
    public GameWindow(String teamName, String major) {
        this.teamName = teamName;
        this.major = major;
        this.logic = new GameLogic();   // starts a new game with a random spawn

        setupPositions();
        buildScreen();
        startTimer();
        updateScreen();
        statusLabel.setText("You spawned at: " + logic.getCurrentLocation() + ". Good luck!");
    }

    /** Decides where each landmark button goes on the map. */
    private void setupPositions() {
        landmarkPositions.put("Lane Ave Structure", new Point(350, 70));
        landmarkPositions.put("The James",          new Point(120, 210));
        landmarkPositions.put("Thompson Library",   new Point(350, 210));
        landmarkPositions.put("Ohio Union",         new Point(350, 350));
        landmarkPositions.put("RPAC",               new Point(590, 370));
    }

    /** Creates all the labels, boxes, and buttons and puts them in the window. */
    private void buildScreen() {
        setTitle("OSU Campus Scavenger Hunt");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // ----- TOP: team name, timer, score -----
        JPanel topPanel = new JPanel(new GridLayout(1, 3));
        teamLabel = new JLabel("", SwingConstants.CENTER);
        timerLabel = new JLabel("", SwingConstants.CENTER);
        scoreLabel = new JLabel("", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 28));
        teamLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        scoreLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        topPanel.add(teamLabel);
        topPanel.add(timerLabel);
        topPanel.add(scoreLabel);
        add(topPanel, BorderLayout.NORTH);

        // ----- CENTER: the clickable map -----
        mapPanel = new MapPanel();
        mapPanel.setLayout(null);   // null layout lets us place buttons at exact x,y spots
        mapPanel.setPreferredSize(new Dimension(720, 440));

        for (String name : GameLogic.LANDMARKS) {
            JButton button = new JButton(name);
            Point p = landmarkPositions.get(name);
            // setBounds(x, y, width, height). We subtract so the button is centered on the point.
            button.setBounds(p.x - 70, p.y - 20, 140, 40);

            // What happens when this button is clicked:
            button.addActionListener(e -> handleGuess(name));

            landmarkButtons.put(name, button);
            mapPanel.add(button);
        }
        add(mapPanel, BorderLayout.CENTER);

        // ----- BOTTOM: riddle text box and status message -----
        JPanel bottomPanel = new JPanel(new BorderLayout());
        riddleBox = new JTextArea(4, 40);
        riddleBox.setEditable(false);        // players can't type in it
        riddleBox.setLineWrap(true);         // wrap long riddles onto new lines
        riddleBox.setWrapStyleWord(true);
        riddleBox.setFont(new Font("Arial", Font.PLAIN, 18));
        bottomPanel.add(new JScrollPane(riddleBox), BorderLayout.CENTER);

        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.ITALIC, 14));
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        pack();                          // size the window to fit everything
        setLocationRelativeTo(null);     // center on screen
    }

    /** Starts the 1-hour countdown. The code inside runs once every 1000 ms (1 second). */
    private void startTimer() {
        countdownTimer = new javax.swing.Timer(1000, e -> {
            secondsRemaining--;
            updateScreen();

            if (secondsRemaining <= 0) {
                timeIsUp();
            }
        });
        countdownTimer.start();
    }

    /** Called when the player clicks a landmark button. */
    private void handleGuess(String clickedLandmark) {
        if (gameOver) {
            return;   // ignore clicks after the game ended
        }

        boolean correct = logic.checkGuess(clickedLandmark);

        if (correct) {
            if (logic.isFinished()) {
                winGame();
            } else {
                statusLabel.setText("Correct! You walked to " + clickedLandmark + ". Here's your next riddle...");
            }
        } else {
            statusLabel.setText("Nope, that's not it! -50 points. Try again.");
        }

        updateScreen();
    }

    /** Refreshes all the text on screen so it matches the current game state. */
    private void updateScreen() {
        // Timer display as MM:SS
        int minutes = secondsRemaining / 60;
        int seconds = secondsRemaining % 60;
        timerLabel.setText(String.format("%02d:%02d", minutes, seconds));

        // Turn the timer red in the last 5 minutes
        if (secondsRemaining <= 300) {
            timerLabel.setForeground(Color.RED);
        } else {
            timerLabel.setForeground(Color.BLACK);
        }

        teamLabel.setText("Team: " + teamName + " (" + major + ")");
        scoreLabel.setText("Score now: " + logic.calculateScore(Math.max(secondsRemaining, 0))
                + "  |  Wrong: " + logic.getWrongGuesses());

        if (!gameOver) {
            riddleBox.setText("RIDDLE " + logic.getRiddleNumber() + " of " + logic.getTotalRiddles()
                    + ":\n" + logic.getCurrentRiddle());
        }

        mapPanel.repaint();   // redraw the map so the "YOU" marker moves
    }

    /** Player solved the last riddle in time! */
    private void winGame() {
        gameOver = true;
        countdownTimer.stop();
        disableButtons();

        final int finalScore = logic.calculateScore(secondsRemaining);
        riddleBox.setText("YOU DID IT! Final destination reached.\nFinal score: " + finalScore);
        statusLabel.setText("Saving your score to the leaderboard...");

        // Sending data over the internet can be slow, so we do it on a separate thread.
        // That way the window doesn't freeze while we wait.
        new Thread(() -> {
            boolean saved = FirebaseLeaderboard.submitScore(teamName, major, finalScore);

            // Swing screens must only be changed from the main screen thread,
            // so we use invokeLater to hop back to it.
            SwingUtilities.invokeLater(() -> {
                if (saved) {
                    statusLabel.setText("Score saved to the global leaderboard!");
                    JOptionPane.showMessageDialog(this,
                            "You win, " + teamName + "!\nFinal score: " + finalScore
                                    + "\nSaved to the leaderboard.");
                } else {
                    statusLabel.setText("Could not save score (check internet / Firebase URL).");
                    JOptionPane.showMessageDialog(this,
                            "You win, " + teamName + "!\nFinal score: " + finalScore
                                    + "\nBut the score could NOT be saved. Check the console for details.");
                }
            });
        }).start();
    }

    /** Timer hit zero before the player finished. */
    private void timeIsUp() {
        gameOver = true;
        countdownTimer.stop();
        disableButtons();
        riddleBox.setText("TIME'S UP! You ran out of time. Better luck next time!");
        statusLabel.setText("Game over.");
        JOptionPane.showMessageDialog(this, "Time's up! Your hour is over.");
    }

    /** Turns off every landmark button. */
    private void disableButtons() {
        for (JButton button : landmarkButtons.values()) {
            button.setEnabled(false);
        }
    }

    // ===================================================================
    // MapPanel: a small panel that paints the green "campus" background
    // and a red "YOU ARE HERE" marker above the player's current landmark.
    // ===================================================================
    private class MapPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Green grass background
            g.setColor(new Color(190, 225, 170));
            g.fillRect(0, 0, getWidth(), getHeight());

            // A scarlet title in the corner
            g.setColor(new Color(187, 0, 0));
            g.setFont(new Font("Arial", Font.BOLD, 16));
            g.drawString("OSU Campus Map (click where you think the riddle points!)", 10, 20);

            // Draw the player's marker just above their current landmark's button
            Point p = landmarkPositions.get(logic.getCurrentLocation());
            if (p != null) {
                g.setColor(Color.RED);
                g.fillOval(p.x - 10, p.y - 52, 20, 20);
                g.setColor(Color.BLACK);
                g.drawString("YOU", p.x - 14, p.y - 58);
            }
        }
    }

    // ===================================================================
    // main: where the program starts
    // ===================================================================
    public static void main(String[] args) {
        // Ask for team name and major with simple pop-up boxes
        String team = JOptionPane.showInputDialog(null, "Enter your Team Name:");
        if (team == null) {
            System.exit(0);   // they pressed Cancel
        }
        String major = JOptionPane.showInputDialog(null, "Enter your Major:");
        if (major == null) {
            System.exit(0);
        }
        if (team.trim().isEmpty()) {
            team = "Unnamed Team";
        }
        if (major.trim().isEmpty()) {
            major = "Undecided";
        }

        // Swing windows should be created with invokeLater (it's the safe, standard way)
        final String finalTeam = team.trim();
        final String finalMajor = major.trim();
        SwingUtilities.invokeLater(() -> {
            GameWindow window = new GameWindow(finalTeam, finalMajor);
            window.setVisible(true);
        });
    }
}