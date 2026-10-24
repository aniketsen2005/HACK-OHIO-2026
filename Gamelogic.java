import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * GameLogic.java
 * ----------------------------------------------------------
 * This file holds the "brain" of the game. It does NOT draw anything.
 * It only remembers:
 *   - where the player is standing
 *   - which riddle they are on
 *   - how many wrong guesses they have made
 *   - how to calculate the score
 *
 * GameWindow.java (the visual part) asks this class questions like
 * "was that click correct?" and "what is the next riddle?".
 */
public class GameLogic {

    // The hard time limit: 1 hour = 3600 seconds
    public static final int TOTAL_SECONDS = 3600;

    // Each wrong guess costs this many points
    private static final int WRONG_GUESS_PENALTY = 50;

    // The names of all landmarks. These MUST match the button names in GameWindow.
    public static final String[] LANDMARKS = {
        "Ohio Union",
        "RPAC",
        "Thompson Library",
        "The James",
        "Lane Ave Structure"
    };

    // One riddle per landmark. The riddle at position 0 is the answer "Ohio Union",
    // position 1 is "RPAC", and so on (same order as LANDMARKS above).
    // TEAMMATES: feel free to rewrite these to be funnier / harder!
    private static final String[] RIDDLES = {
        "I am the living room of campus. Student orgs, food, and events all live under my roof on High Street. Where am I?",
        "Climb my rock wall, swim in my pools, lift in my gym. Students sweat here to stay in shape. Where am I?",
        "Shhh! Millions of books live here, and my big reading room is a favorite spot for finals week. I sit near the Oval. Where am I?",
        "I am a cancer hospital and research center, and my name honors a man named James. Where am I?",
        "Cars circle my many levels looking for a spot to park near Lane Avenue. I am not a building for classes, just wheels. Where am I?"
    };

    // ---- Game state (things that change while playing) ----
    private List<Integer> route;        // the order of landmark numbers the player must visit
    private int currentStep;            // which riddle we are on (0 = first riddle)
    private String currentLocation;     // the landmark the player is standing at
    private int wrongGuesses;           // how many wrong clicks so far

    /**
     * Constructor: runs once when a new game starts.
     * Picks a random starting landmark and shuffles the rest into a route.
     */
    public GameLogic() {
        Random random = new Random();

        // 1. Pick a random starting landmark (this is where the player "spawns")
        int startIndex = random.nextInt(LANDMARKS.length);
        currentLocation = LANDMARKS[startIndex];

        // 2. Make a list of every OTHER landmark, then shuffle it.
        //    That shuffled list is the order of destinations (the last one is the final destination).
        route = new ArrayList<>();
        for (int i = 0; i < LANDMARKS.length; i++) {
            if (i != startIndex) {
                route.add(i);
            }
        }
        Collections.shuffle(route);

        currentStep = 0;
        wrongGuesses = 0;
    }

    /** Where the player is standing right now. */
    public String getCurrentLocation() {
        return currentLocation;
    }

    /** The riddle the player needs to solve right now. */
    public String getCurrentRiddle() {
        int landmarkIndex = route.get(currentStep);
        return RIDDLES[landmarkIndex];
    }

    /**
     * Checks a button click.
     * Returns true if the player clicked the right landmark.
     * If right: the player "walks" there and we move to the next riddle.
     * If wrong: we add one to the wrong guess counter.
     */
    public boolean checkGuess(String clickedLandmark) {
        int correctIndex = route.get(currentStep);
        String correctAnswer = LANDMARKS[correctIndex];

        if (clickedLandmark.equals(correctAnswer)) {
            currentLocation = correctAnswer;   // walk to the landmark
            currentStep++;                     // unlock the next riddle
            return true;
        } else {
            wrongGuesses++;
            return false;
        }
    }

    /** True once the player has solved every riddle (reached the final destination). */
    public boolean isFinished() {
        return currentStep >= route.size();
    }

    public int getWrongGuesses() {
        return wrongGuesses;
    }

    /** Which riddle number are we on? (1-based, for showing "Riddle 2 of 4"). */
    public int getRiddleNumber() {
        return currentStep + 1;
    }

    public int getTotalRiddles() {
        return route.size();
    }

    /**
     * Score formula from the rules:
     *   Score = 1000 * (Time Remaining / 3600) - (Wrong Guesses * 50)
     * We never let the score go below 0.
     */
    public int calculateScore(int secondsRemaining) {
        double timeScore = 1000.0 * ((double) secondsRemaining / TOTAL_SECONDS);
        double score = timeScore - (wrongGuesses * WRONG_GUESS_PENALTY);
        if (score < 0) {
            score = 0;
        }
        return (int) Math.round(score);
    }
}