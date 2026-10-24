import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Objects;

/**
 * GameWindow.java
 * ----------------------------------------------------------
 * The visual part of the game. It shows:
 *   - a countdown timer (5 minutes until the exam!)
 *   - the current riddle (and a HINT button)
 *   - a map of campus with one clickable DOT per landmark
 *       red dot  = not visited yet
 *       gray dot = you have been there
 *     (move the mouse over a dot to see the building's name)
 *   - a "YOU" marker that WALKS to each landmark you solve
 *     and gets TELEPORTED when you guess wrong
 *   - an end-of-game message based on your score, and a PLAY AGAIN button
 *
 * MAP BACKGROUND: the game looks for a picture file named
 *   campus_map.png
 * in the project's main folder (the same folder as the .java files).
 * If the file is missing, the game still works with a plain green background.
 *
 * It uses GameLogic.java for the rules/score/landmarks and
 * FirebaseLeaderboard.java to save the final score.
 *
 * TO RUN: open this file in VSCode and click "Run" above the main method.
 */
public class GameWindow extends JFrame {

    // ---- Sizes (the map picture gets stretched to MAP_WIDTH x MAP_HEIGHT) ----
    // If you change these, change them in MapCalibrator.java too!
    private static final int MAP_WIDTH = 900;
    private static final int MAP_HEIGHT = 560;

    // ---- Dots ----
    private static final int DOT_RADIUS = 9;          // normal dot size
    private static final int DOT_HOVER_RADIUS = 12;   // dot size while the mouse is over it
    private static final int CLICK_RANGE = 15;        // how close (in pixels) the mouse must be to a dot

    // The timer turns red when this many seconds (or fewer) are left
    private static final int LOW_TIME_SECONDS = 60;

    // ---- Colors ----
    private static final Color SCARLET = new Color(187, 0, 0);
    private static final Color SCARLET_DARK = new Color(120, 0, 0);   // unvisited dot while hovering
    private static final Color VISITED_GRAY = new Color(125, 125, 125);
    private static final Color VISITED_GRAY_DARK = new Color(70, 70, 70);

    // ---- Game data ----
    private GameLogic logic;                                   // the game brain
    private String playerName;
    private String major;
    private int secondsRemaining = GameLogic.TOTAL_SECONDS;   // starts at 300 (5 minutes)
    private boolean gameOver = false;
    private int gameNumber = 0;   // goes up by 1 every time a new game starts

    // ---- Things we see on screen ----
    private JLabel playerLabel;
    private JLabel timerLabel;
    private JLabel scoreLabel;
    private JTextPane riddleBox;   // the big text box at the bottom (riddles, and the end-of-game message)
    private Insets boxMargin;      // the normal spacing around the text in that box
    private JLabel statusLabel;
    private JButton hintButton;
    private JButton playAgainButton;
    private MapPanel mapPanel;

    // The name of the landmark the mouse is currently over (null = not over any dot)
    private String hoveredName = null;

    // ---- The map picture (null if campus_map.png wasn't found) ----
    private BufferedImage campusMap;

    // ---- The walking "YOU" marker ----
    // markerX/markerY is where the marker is drawn right now.
    // targetX/targetY is where it is walking to.
    private double markerX;
    private double markerY;
    private int targetX;
    private int targetY;
    private javax.swing.Timer walkTimer;   // moves the marker a little bit every 30 ms

    // ---- The teleport flash (a gold ring that grows and fades) ----
    private int pulseFrame = -1;           // -1 means "no flash right now"
    private javax.swing.Timer pulseTimer;

    // The countdown timer. It "ticks" once per second.
    // (javax.swing.Timer is the beginner-friendly, safe way to do a timer in Swing.)
    private javax.swing.Timer countdownTimer;

    /**
     * Constructor: builds the whole window (but does NOT start the clock yet).
     */
    public GameWindow(String playerName, String major) {
        this.playerName = playerName;
        this.major = major;

        loadMapImage();
        buildScreen();
        resetGame();   // sets up game number 1
    }

    /** Tries to load campus_map.png. If it isn't there, the game uses a green background instead. */
    private void loadMapImage() {
        try {
            File file = new File("campus_map.png");
            if (file.exists()) {
                campusMap = ImageIO.read(file);
            } else {
                System.out.println("campus_map.png not found - using the plain green map.");
            }
        } catch (Exception e) {
            System.out.println("Could not load campus_map.png: " + e.getMessage());
            campusMap = null;
        }
    }

    /**
     * Gets everything ready for a brand-new game:
     * a new random spawn + exam building, a fresh timer, and a fresh score.
     * Used when the window first opens AND every time the player clicks Play Again.
     */
    private void resetGame() {
        // Stop anything still running from the last game
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        if (walkTimer != null) {
            walkTimer.stop();
        }
        if (pulseTimer != null) {
            pulseTimer.stop();
        }
        pulseFrame = -1;

        gameNumber++;
        logic = new GameLogic();   // new random spawn + random exam building
        secondsRemaining = GameLogic.TOTAL_SECONDS;
        gameOver = false;
        hoveredName = null;
        mapPanel.setCursor(Cursor.getDefaultCursor());

        // Put the marker on the landmark where the player spawned
        GameLogic.Landmark spawn = GameLogic.findLandmark(logic.getCurrentLocation());
        markerX = spawn.x;
        markerY = spawn.y;
        targetX = spawn.x;
        targetY = spawn.y;

        updateScreen();
        statusLabel.setText("You spawned at: " + logic.getCurrentLocation() + ". Good luck!");
    }

    /**
     * Shows the story pop-up, and only starts the clock once the player clicks OK.
     * (Called after the window is visible, and again for every Play Again.)
     */
    public void begin() {
        JOptionPane.showMessageDialog(this, logic.getStoryIntro(),
                "Your exam is in " + (GameLogic.TOTAL_SECONDS / 60) + " minutes!",
                JOptionPane.INFORMATION_MESSAGE);
        startTimer();
    }

    /** Starts a new game right away (used by both Play Again buttons). */
    private void playAgain() {
        resetGame();
        begin();
    }

    /** Creates all the labels, boxes, and the map and puts them in the window. */
    private void buildScreen() {
        setTitle("OSU Exam Scavenger Hunt");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // ----- TOP: player name, timer, score -----
        JPanel topPanel = new JPanel(new GridLayout(1, 3));
        playerLabel = new JLabel("", SwingConstants.CENTER);
        timerLabel = new JLabel("", SwingConstants.CENTER);
        scoreLabel = new JLabel("", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 26));
        playerLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        scoreLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        topPanel.add(playerLabel);
        topPanel.add(timerLabel);
        topPanel.add(scoreLabel);
        add(topPanel, BorderLayout.NORTH);

        // ----- CENTER: the clickable map -----
        mapPanel = new MapPanel();
        mapPanel.setPreferredSize(new Dimension(MAP_WIDTH, MAP_HEIGHT));

        // When the mouse MOVES over the map: figure out if it is over a dot.
        mapPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                String found = landmarkAt(e.getX(), e.getY());

                // Only redraw if something changed (saves work)
                if (!Objects.equals(found, hoveredName)) {
                    hoveredName = found;

                    // Hand cursor over a dot while the game is running
                    if (found != null && !gameOver) {
                        mapPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    } else {
                        mapPanel.setCursor(Cursor.getDefaultCursor());
                    }
                    mapPanel.repaint();
                }
            }
        });

        mapPanel.addMouseListener(new MouseAdapter() {
            // When the mouse is CLICKED on the map: if it is on a dot, that's a guess.
            @Override
            public void mousePressed(MouseEvent e) {
                String found = landmarkAt(e.getX(), e.getY());
                if (found != null) {
                    handleGuess(found);
                }
            }

            // When the mouse leaves the map, hide the name tag
            @Override
            public void mouseExited(MouseEvent e) {
                hoveredName = null;
                mapPanel.repaint();
            }
        });

        add(mapPanel, BorderLayout.CENTER);

        // ----- BOTTOM: riddle text box, buttons, and status message -----
        JPanel bottomPanel = new JPanel(new BorderLayout());
        riddleBox = new JTextPane();         // wraps long text by itself, and can center text
        riddleBox.setEditable(false);        // players can't type in it
        boxMargin = riddleBox.getMargin();   // remember the normal edge spacing
        JScrollPane riddleScroll = new JScrollPane(riddleBox);
        riddleScroll.setPreferredSize(new Dimension(600, 120));
        bottomPanel.add(riddleScroll, BorderLayout.CENTER);

        // Three stacked buttons on the right: Hint, Leaderboard, Play Again
        JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 0, 4));

        hintButton = new JButton("Hint (-" + GameLogic.HINT_COST + " pts)");
        hintButton.addActionListener(e -> handleHint());
        buttonPanel.add(hintButton);

        JButton leaderboardButton = new JButton("View Leaderboard");
        leaderboardButton.addActionListener(e -> LeaderboardWindow.showWindow());
        buttonPanel.add(leaderboardButton);

        // Only clickable once a game has ended (it is turned on in updateScreen)
        playAgainButton = new JButton("Play Again");
        playAgainButton.addActionListener(e -> playAgain());
        buttonPanel.add(playAgainButton);

        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.ITALIC, 14));
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        pack();                          // size the window to fit everything
        setLocationRelativeTo(null);     // center on screen
    }

    /**
     * Finds which landmark dot is at a mouse position.
     * Returns that landmark's name, or null if the mouse isn't close to any dot.
     * (If two dots are close together, it picks the closest one.)
     */
    private String landmarkAt(int mouseX, int mouseY) {
        String closestName = null;
        double closestDistance = CLICK_RANGE;   // only dots within this range count

        for (GameLogic.Landmark landmark : GameLogic.LANDMARKS) {
            double dx = mouseX - landmark.x;
            double dy = mouseY - landmark.y;
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance <= closestDistance) {
                closestDistance = distance;
                closestName = landmark.name;
            }
        }
        return closestName;
    }

    /** Starts the countdown. The code inside runs once every 1000 ms (1 second). */
    private void startTimer() {
        countdownTimer = new javax.swing.Timer(1000, e -> {
            secondsRemaining--;
            updateScreen();

            if (secondsRemaining <= 0) {
                loseGame("Time ran out!");
            } else {
                checkForLoss();   // the score also drains as time passes
            }
        });
        countdownTimer.start();
    }

    /** If the score has dropped to 0, the player loses. */
    private void checkForLoss() {
        if (!gameOver && logic.calculateScore(secondsRemaining) <= 0) {
            loseGame("Your score hit 0!");
        }
    }

    /**
     * Makes the "YOU" marker walk to a spot on the map.
     * A small timer moves it a few pixels every 30 ms, so it glides instead of jumping.
     */
    private void walkTo(int x, int y) {
        targetX = x;
        targetY = y;

        // If the marker is already walking, it will just head toward the new target.
        if (walkTimer != null && walkTimer.isRunning()) {
            return;
        }

        walkTimer = new javax.swing.Timer(30, e -> {
            double dx = targetX - markerX;
            double dy = targetY - markerY;
            double distance = Math.sqrt(dx * dx + dy * dy);
            double step = 12;   // pixels moved per tick. Bigger number = faster walking.

            if (distance <= step) {
                // Close enough: snap to the target and stop walking
                markerX = targetX;
                markerY = targetY;
                walkTimer.stop();
            } else {
                // Take one step toward the target
                markerX += dx / distance * step;
                markerY += dy / distance * step;
            }
            mapPanel.repaint();
        });
        walkTimer.start();
    }

    /** Instantly moves the "YOU" marker to a landmark and plays the gold flash. (The punishment!) */
    private void teleportMarkerTo(String landmarkName) {
        GameLogic.Landmark spot = GameLogic.findLandmark(landmarkName);

        // Stop any walking that was in progress
        if (walkTimer != null) {
            walkTimer.stop();
        }
        markerX = spot.x;
        markerY = spot.y;
        targetX = spot.x;
        targetY = spot.y;

        startPulse();
    }

    /** Starts the gold ring that grows and fades around the marker (about 0.6 seconds). */
    private void startPulse() {
        if (pulseTimer != null) {
            pulseTimer.stop();
        }
        pulseFrame = 0;
        pulseTimer = new javax.swing.Timer(30, e -> {
            pulseFrame++;
            if (pulseFrame > 20) {
                pulseFrame = -1;     // done
                pulseTimer.stop();
            }
            mapPanel.repaint();
        });
        pulseTimer.start();
    }

    /** Called when the player clicks a landmark dot. */
    private void handleGuess(String clickedLandmark) {
        if (gameOver) {
            return;   // ignore clicks after the game ended
        }

        GameLogic.Result result = logic.checkGuess(clickedLandmark);

        switch (result) {
            case ALREADY_HERE:
                statusLabel.setText("You're already standing at " + clickedLandmark + "! Pick a different dot.");
                break;

            case CORRECT: {
                // Send the marker walking to the landmark we just reached
                GameLogic.Landmark reached = GameLogic.findLandmark(clickedLandmark);
                walkTo(reached.x, reached.y);

                String message = "Correct! You walked to " + clickedLandmark + ".";
                if (logic.getLastBonus() > 0) {
                    message += "  STREAK x" + logic.getStreak() + ": +" + logic.getLastBonus() + " bonus!";
                }
                statusLabel.setText(message + " Here's your next riddle...");
                break;
            }

            case WON: {
                GameLogic.Landmark reached = GameLogic.findLandmark(clickedLandmark);
                walkTo(reached.x, reached.y);
                winGame();
                break;
            }

            case WRONG:
                // The punishment: you get teleported somewhere else and the riddle gets harder
                teleportMarkerTo(logic.getCurrentLocation());
                statusLabel.setText("Wrong! -50 points. ZAP! You were teleported to "
                        + logic.getCurrentLocation() + ". Solve this HARDER riddle!");
                break;
        }

        updateScreen();

        if (result == GameLogic.Result.WRONG) {
            checkForLoss();   // that wrong guess might have dropped the score to 0
        }
    }

    /** Called when the player clicks the Hint button. */
    private void handleHint() {
        if (gameOver) {
            return;
        }

        if (!logic.canUseHint(secondsRemaining)) {
            statusLabel.setText("Not enough points for a hint!");
            return;
        }

        logic.useHint();
        statusLabel.setText("Hint used! -" + GameLogic.HINT_COST + " points.");
        updateScreen();
        checkForLoss();
    }

    /** Refreshes all the text on screen so it matches the current game state. */
    private void updateScreen() {
        // Timer display as MM:SS
        int minutes = Math.max(secondsRemaining, 0) / 60;
        int seconds = Math.max(secondsRemaining, 0) % 60;
        timerLabel.setText(String.format("Exam in %02d:%02d", minutes, seconds));

        // Turn the timer red when time is almost up
        if (secondsRemaining <= LOW_TIME_SECONDS) {
            timerLabel.setForeground(Color.RED);
        } else {
            timerLabel.setForeground(Color.BLACK);
        }

        playerLabel.setText("Player: " + playerName + " (" + major + ")");
        scoreLabel.setText("Score " + logic.calculateScore(secondsRemaining)
                + "   Wrong " + logic.getWrongGuesses()
                + "   Streak " + logic.getStreak());

        if (!gameOver) {
            // The last riddle gets a special heading, since it leads to the exam
            String heading;
            if (logic.isFinalClue()) {
                heading = "FINAL RIDDLE - THIS LEADS TO YOUR EXAM!";
            } else {
                heading = "CHECKPOINT " + logic.getCheckpointNumber() + " of " + logic.getCheckpointTotal();
            }
            if (logic.isHardMode()) {
                heading = heading + "   [HARD RIDDLE - your punishment!]";
            }

            if (logic.isHintShown()) {
                showLeftText(heading + ":", logic.getCurrentRiddle(), "", "HINT: " + logic.getHintText());
            } else {
                showLeftText(heading + ":", logic.getCurrentRiddle());
            }
        }

        // The hint button only works once per riddle, and not after the game ends
        hintButton.setEnabled(!gameOver && !logic.isHintShown());

        // The Play Again button only works once the game has ended
        playAgainButton.setEnabled(gameOver);

        mapPanel.repaint();   // redraw the map
    }

    /** Player reached the exam building in time! */
    private void winGame() {
        final int finalScore = logic.calculateScore(secondsRemaining);

        // Reached the exam but already at 0 points? That still counts as a loss.
        if (finalScore <= 0) {
            loseGame("Your score hit 0!");
            return;
        }

        gameOver = true;
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        mapPanel.setCursor(Cursor.getDefaultCursor());

        // The message depends on how high the score is (see SCORE_TIERS in GameLogic.java)
        final String rankTitle = GameLogic.getRankTitle(finalScore);
        final String endMessage = GameLogic.getEndMessage(finalScore);
        final int thisGame = gameNumber;

        // The bottom box shows the headline for the score, centered
        showCenteredText(rankTitle, null, null);   // bottom box: just the headline (the pop-up has the rest)
        statusLabel.setText("Saving your score to the leaderboard...");
        updateScreen();

        // Sending data over the internet can be slow, so we do it on a separate thread.
        // That way the window doesn't freeze while we wait.
        new Thread(() -> {
            boolean saved = FirebaseLeaderboard.submitScore(playerName, major, finalScore);

            // Swing screens must only be changed from the main screen thread,
            // so we use invokeLater to hop back to it.
            SwingUtilities.invokeLater(() -> {
                // If the player already started a new game, don't pop up an old result
                if (thisGame != gameNumber) {
                    return;
                }

                String saveNote;
                if (saved) {
                    statusLabel.setText("Score saved to the global leaderboard!");
                    saveNote = "Your score was saved to the leaderboard.";
                } else {
                    statusLabel.setText("Could not save score (check internet / Firebase URL).");
                    saveNote = "(Your score could NOT be saved. Check the console for details.)";
                }

                showEndDialog("YOU MADE IT TO YOUR EXAM!", endMessage, "Final score: " + finalScore, saveNote);
            });
        }).start();
    }

    /** The player lost: the score hit 0, or the time ran out. */
    private void loseGame(String reason) {
        gameOver = true;
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        mapPanel.setCursor(Cursor.getDefaultCursor());

        showCenteredText("GAME OVER", null, null);   // bottom box: just the headline
        statusLabel.setText("Game over.");
        updateScreen();

        showEndDialog("YOU MISSED YOUR EXAM!", reason + " Ready to try again?", null, null);
    }

    /**
     * The end-of-game pop-up. It has three buttons:
     *   Play Again       - starts a brand-new game
     *   View Leaderboard - opens the leaderboard window
     *   Close            - just closes the pop-up (the Play Again button on the main window still works)
     */
    private void showEndDialog(String title, String message, String scoreLine, String smallNote) {
        String[] options = {"Play Again", "View Leaderboard", "Close"};

        // We build the pop-up ourselves (instead of JOptionPane) so the buttons are
        // centered under the text. On a Mac, JOptionPane pushes the buttons to the right.
        final int[] picked = {-1};   // which button was clicked (-1 = none / closed with the X)
        final javax.swing.JDialog dialog = new javax.swing.JDialog(this, title, true);   // true = must answer first

        JPanel buttonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 12, 8));
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            JButton button = new JButton(options[i]);
            button.addActionListener(e -> {
                picked[0] = index;
                dialog.dispose();
            });
            buttonRow.add(button);
        }

        dialog.setLayout(new BorderLayout());
        dialog.add(makeDialogText(message, scoreLine, smallNote), BorderLayout.CENTER);
        dialog.add(buttonRow, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton((JButton) buttonRow.getComponent(0));   // Enter = Play Again
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);   // waits here until a button is clicked

        int choice = picked[0];
        if (choice == 0) {
            playAgain();
        } else if (choice == 1) {
            LeaderboardWindow.showWindow();
        }
    }

    // ===================================================================
    // Text helpers: the big box at the bottom of the window, and the pop-up text
    // ===================================================================

    /** Shows left-aligned text in the bottom box (used for the riddles). Each line is its own row. */
    private void showLeftText(String... lines) {
        int[] sizes = new int[lines.length];
        boolean[] bold = new boolean[lines.length];
        for (int i = 0; i < lines.length; i++) {
            sizes[i] = 17;
            bold[i] = false;
        }
        fillBox(StyleConstants.ALIGN_LEFT, lines, sizes, bold);
    }

    /**
     * Shows CENTERED text in the bottom box (used when the game ends):
     * a big bold headline, a message under it, and an optional bold last line.
     * There are no blank lines between them, so no wasted space.
     */
    private void showCenteredText(String headline, String message, String lastLine) {
        if (message == null) {
            // headline only, a bit bigger since it stands alone
            fillBox(StyleConstants.ALIGN_CENTER,
                    new String[] {headline},
                    new int[] {34},
                    new boolean[] {true});
        } else if (lastLine == null) {
            fillBox(StyleConstants.ALIGN_CENTER,
                    new String[] {headline, message},
                    new int[] {26, 17},
                    new boolean[] {true, false});
        } else {
            fillBox(StyleConstants.ALIGN_CENTER,
                    new String[] {headline, message, lastLine},
                    new int[] {26, 17, 21},
                    new boolean[] {true, false, true});
        }
    }

    /** Puts lines of text into the bottom box, each with its own font size and bold setting. */
    private void fillBox(int alignment, String[] lines, int[] sizes, boolean[] bold) {
        StyledDocument doc = riddleBox.getStyledDocument();
        riddleBox.setMargin(boxMargin);   // start from the normal edge spacing
        try {
            doc.remove(0, doc.getLength());   // clear the old text

            for (int i = 0; i < lines.length; i++) {
                SimpleAttributeSet style = new SimpleAttributeSet();
                StyleConstants.setFontFamily(style, "Arial");
                StyleConstants.setFontSize(style, sizes[i]);
                StyleConstants.setBold(style, bold[i]);

                String text = lines[i] + (i < lines.length - 1 ? "\n" : "");
                doc.insertString(doc.getLength(), text, style);
            }

            // Left-align or center every line
            SimpleAttributeSet paragraph = new SimpleAttributeSet();
            StyleConstants.setAlignment(paragraph, alignment);
            doc.setParagraphAttributes(0, doc.getLength(), paragraph, false);
        } catch (BadLocationException e) {
            riddleBox.setText(String.join("\n", lines));   // plain fallback, should never happen
        }

        // Centered (end-of-game) text is also centered UP and DOWN in the box,
        // so the empty space is split evenly above and below instead of piling up at the bottom.
        if (alignment == StyleConstants.ALIGN_CENTER && riddleBox.getParent() != null) {
            int boxHeight = riddleBox.getParent().getHeight();        // the visible height of the box
            int textHeight = riddleBox.getPreferredSize().height;     // how tall the text is
            int extra = boxHeight - textHeight;
            if (extra > 0) {
                riddleBox.setMargin(new Insets(boxMargin.top + extra / 2,
                        boxMargin.left, boxMargin.bottom, boxMargin.right));
            }
        }

        riddleBox.setCaretPosition(0);   // show the top of the text
    }

    /**
     * Builds the (bigger, centered) text for the end-of-game pop-up:
     * the message, then an optional big score line, then an optional small note.
     * Swing labels can show simple HTML, which is an easy way to get centered text with different sizes.
     */
    private JLabel makeDialogText(String message, String scoreLine, String smallNote) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='width:480px; font-family:Arial'><div align='center'>");
        html.append("<div style='font-size:20pt'>").append(escapeHtml(message)).append("</div>");
        if (scoreLine != null) {
            html.append("<div style='font-size:28pt; font-weight:bold; margin-top:10px'>")
                    .append(escapeHtml(scoreLine)).append("</div>");
        }
        if (smallNote != null) {
            html.append("<div style='font-size:15pt; color:#555555; margin-top:6px'>")
                    .append(escapeHtml(smallNote)).append("</div>");
        }
        html.append("</div></body></html>");
        JLabel label = new JLabel(html.toString());
        label.setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 20, 4, 20));   // even spacing around the text
        return label;
    }

    /** Makes text safe to put inside HTML (so characters like < and & can't break it). */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ===================================================================
    // MapPanel: paints the campus picture (or a green background),
    // the landmark dots, the teleport flash, the "YOU" marker,
    // and the hover name tag.
    // ===================================================================
    private class MapPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();

            // Smooth edges and smooth picture scaling
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // ----- Background: the campus picture, or plain green if it's missing -----
            if (campusMap != null) {
                g2.drawImage(campusMap, 0, 0, getWidth(), getHeight(), this);
            } else {
                g2.setColor(new Color(190, 225, 170));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }

            // ----- Title strip across the top -----
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fillRect(0, 0, getWidth(), 28);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 14));
            g2.drawString("Hover a dot for the name, click to walk there.   Red = new place, gray = visited.", 10, 19);

            // ----- One dot for every landmark -----
            for (GameLogic.Landmark landmark : GameLogic.LANDMARKS) {
                boolean hovered = landmark.name.equals(hoveredName);
                boolean visited = logic.isVisited(landmark.name);
                int r = hovered ? DOT_HOVER_RADIUS : DOT_RADIUS;

                // white ring (so the dot stands out on the busy satellite picture)
                g2.setColor(Color.WHITE);
                g2.fillOval(landmark.x - r - 2, landmark.y - r - 2, 2 * r + 4, 2 * r + 4);

                // the dot: scarlet if new, gray if visited (darker while hovering)
                if (visited) {
                    g2.setColor(hovered ? VISITED_GRAY_DARK : VISITED_GRAY);
                } else {
                    g2.setColor(hovered ? SCARLET_DARK : SCARLET);
                }
                g2.fillOval(landmark.x - r, landmark.y - r, 2 * r, 2 * r);
            }

            // ----- The teleport flash: a gold ring that grows and fades -----
            int dotX = (int) Math.round(markerX);
            int dotY = (int) Math.round(markerY);
            if (pulseFrame >= 0) {
                int ringRadius = 12 + pulseFrame * 4;
                int alpha = Math.max(0, 255 - pulseFrame * 12);
                g2.setStroke(new BasicStroke(4f));
                g2.setColor(new Color(255, 200, 0, alpha));
                g2.drawOval(dotX - ringRadius, dotY - ringRadius, ringRadius * 2, ringRadius * 2);
                g2.setStroke(new BasicStroke(1f));
            }

            // ----- The "YOU" marker, drawn just above the dot it is standing at -----
            int mx = dotX;
            int my = dotY - 30;

            // circle with a white outline
            g2.setColor(Color.WHITE);
            g2.fillOval(mx - 12, my - 12, 24, 24);
            g2.setColor(new Color(0, 90, 200));   // blue, so it doesn't look like a landmark dot
            g2.fillOval(mx - 9, my - 9, 18, 18);

            // "YOU" label on a dark rounded box above the circle
            String label = "YOU";
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            FontMetrics metrics = g2.getFontMetrics();
            int labelWidth = metrics.stringWidth(label);
            g2.setColor(new Color(0, 0, 0, 170));
            g2.fillRoundRect(mx - labelWidth / 2 - 4, my - 34, labelWidth + 8, 18, 8, 8);
            g2.setColor(Color.WHITE);
            g2.drawString(label, mx - labelWidth / 2, my - 21);

            // ----- The name tag, drawn LAST so it sits on top of everything -----
            if (hoveredName != null) {
                GameLogic.Landmark hoveredLandmark = GameLogic.findLandmark(hoveredName);

                g2.setFont(new Font("Arial", Font.BOLD, 14));
                FontMetrics tagMetrics = g2.getFontMetrics();
                int tagWidth = tagMetrics.stringWidth(hoveredName) + 18;
                int tagHeight = 26;

                // Center the tag under the dot, but keep it inside the map edges
                int tagX = hoveredLandmark.x - tagWidth / 2;
                tagX = Math.max(4, Math.min(tagX, getWidth() - tagWidth - 4));

                // Put the tag below the dot (the YOU marker is above it).
                // If there's no room at the bottom, put it above instead.
                int tagY = hoveredLandmark.y + DOT_HOVER_RADIUS + 8;
                if (tagY + tagHeight > getHeight() - 4) {
                    tagY = hoveredLandmark.y - DOT_HOVER_RADIUS - 8 - tagHeight;
                }

                g2.setColor(new Color(20, 20, 20, 235));
                g2.fillRoundRect(tagX, tagY, tagWidth, tagHeight, 10, 10);
                g2.setColor(Color.WHITE);
                g2.drawString(hoveredName, tagX + 9, tagY + 18);
            }

            g2.dispose();
        }
    }

    // ===================================================================
    // main: where the program starts
    // ===================================================================
    public static void main(String[] args) {
        // Ask for the player name and major with simple pop-up boxes
        String player = JOptionPane.showInputDialog(null, "Enter your Player Name:");
        if (player == null) {
            System.exit(0);   // they pressed Cancel
        }
        String major = JOptionPane.showInputDialog(null, "Enter your Major:");
        if (major == null) {
            System.exit(0);
        }
        if (player.trim().isEmpty()) {
            player = "Unnamed Player";
        }
        if (major.trim().isEmpty()) {
            major = "Undecided";
        }

        // Swing windows should be created with invokeLater (it's the safe, standard way)
        final String finalPlayer = player.trim();
        final String finalMajor = major.trim();
        SwingUtilities.invokeLater(() -> {
            GameWindow window = new GameWindow(finalPlayer, finalMajor);
            window.setVisible(true);
            window.begin();   // show the story pop-up, then start the clock
        });
    }
}