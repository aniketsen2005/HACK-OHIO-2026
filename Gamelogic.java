import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 * THE STORY: The player has an EXAM in 1 hour but is lost on campus.
 * Each game picks a RANDOM starting landmark and a RANDOM exam building.
 * The player follows a few checkpoint riddles, and the LAST riddle
 * leads to the exam building.
 *
 * GameWindow.java (the visual part) asks this class questions like
 * "was that click correct?" and "what is the next riddle?".
 */
public class GameLogic {

    // The hard time limit: 1 hour = 3600 seconds
    public static final int TOTAL_SECONDS = 3600;

    // Each wrong guess costs this many points
    private static final int WRONG_GUESS_PENALTY = 50;

    // How many checkpoint riddles come BEFORE the final exam riddle.
    // 3 checkpoints + 1 exam = 4 riddles per game. Change this to make games longer/shorter.
    // (Keep it at or below LANDMARKS.length - 2.)
    private static final int CHECKPOINTS_BEFORE_EXAM = 3;

    // ===================================================================
    // Landmark: a tiny "package" holding everything about ONE place.
    //   name   = text shown on the button
    //   riddle = the clue whose answer is this place
    //   x, y   = where its button sits on the map (in pixels)
    // ===================================================================
    public static class Landmark {
        public final String name;
        public final String riddle;
        public final int x;
        public final int y;

        public Landmark(String name, String riddle, int x, int y) {
            this.name = name;
            this.riddle = riddle;
            this.x = x;
            this.y = y;
        }
    }

    // ===================================================================
    // ALL THE LANDMARKS.
    // TO ADD A NEW ONE: copy a line, change the name/riddle, and pick an
    // x,y spot that doesn't overlap another button. That's it!
    // The map is about 920 wide and 520 tall. These spots are a simple
    // grid, not the real campus layout.
    // TEAMMATES: feel free to rewrite the riddles to be funnier / harder!
    // ===================================================================
    public static final Landmark[] LANDMARKS = {
        new Landmark("Ohio Stadium",
            "I'm shaped like a horseshoe and over 100,000 fans fill my seats on game day. The marching band dots the 'i' in me. Where am I?",
            130, 110),
        new Landmark("Lane Ave Structure",
            "Cars circle my many levels looking for a spot to park near Lane Avenue. I'm not for classes, just wheels. Where am I?",
            350, 110),
        new Landmark("Schottenstein Center",
            "Buckeye basketball and hockey fans pack my arena. Everyone just calls me 'The Schott'. Where am I?",
            570, 110),
        new Landmark("Dreese Labs",
            "Computer science and engineering students practically live in my labs, especially during finals week. Where am I?",
            790, 110),
        new Landmark("The James",
            "I am a cancer hospital and research center, and my name honors a man named James. Where am I?",
            130, 280),
        new Landmark("Mirror Lake",
            "Each year before the big rivalry game, brave Buckeyes jump into me. Brrr! Where am I?",
            350, 280),
        new Landmark("Thompson Library",
            "Shhh! Millions of books live here, and my big reading room is a favorite spot for finals week. I sit near the Oval. Where am I?",
            570, 280),
        new Landmark("Orton Hall",
            "My tower rings out chimes over campus, and a geology museum and library live inside me. Where am I?",
            790, 280),
        new Landmark("RPAC",
            "Climb my rock wall, swim in my pools, lift in my gym. Students sweat here to stay in shape. Where am I?",
            130, 450),
        new Landmark("The Oval",
            "I'm the big green heart of campus, a wide-open lawn where students toss frisbees and walk to class. Where am I?",
            350, 450),
        new Landmark("Ohio Union",
            "I am the living room of campus. Student orgs, food, and events all live under my roof on High Street. Where am I?",
            570, 450)
    };

    // ---- Game state (things that change while playing) ----
    private List<Landmark> route;       // the landmarks to visit in order (the LAST one is the exam!)
    private int currentStep;            // which riddle we are on (0 = first riddle)
    private String currentLocation;     // name of the landmark the player is standing at
    private int wrongGuesses;           // how many wrong clicks so far

    /**
     * Constructor: runs once when a new game starts.
     * Randomly picks the start, the checkpoints, and the exam building.
     */
    public GameLogic() {
        // 1. Put all the landmarks in a list and shuffle it.
        List<Landmark> shuffled = new ArrayList<>();
        Collections.addAll(shuffled, LANDMARKS);
        Collections.shuffle(shuffled);

        // 2. After shuffling:
        //    - the 1st landmark is where the player spawns
        //    - the 2nd landmark is the EXAM building
        //    - the next few are the checkpoints on the way
        //    Because they come from one shuffled list, none of them repeat.
        Landmark start = shuffled.get(0);
        Landmark exam = shuffled.get(1);
        currentLocation = start.name;

        // 3. Build the route: checkpoints first, exam LAST.
        route = new ArrayList<>();
        for (int i = 0; i < CHECKPOINTS_BEFORE_EXAM; i++) {
            route.add(shuffled.get(2 + i));
        }
        route.add(exam);

        currentStep = 0;
        wrongGuesses = 0;
    }

    /** Finds a landmark by its name (returns null if there's no match). */
    public static Landmark findLandmark(String name) {
        for (Landmark landmark : LANDMARKS) {
            if (landmark.name.equals(name)) {
                return landmark;
            }
        }
        return null;
    }

    /** The story text shown in a pop-up when the game begins. */
    public String getStoryIntro() {
        return "You have an EXAM in 1 hour... but you're lost on campus!\n\n"
                + "You are standing at: " + currentLocation + "\n\n"
                + "Read each riddle, then click the matching landmark on the map to walk there.\n"
                + "The FINAL riddle leads to your exam building.\n\n"
                + "Each wrong guess costs 50 points, and your score drops as time runs out.\n"
                + "Click OK to start the clock!";
    }

    /** Where the player is standing right now. */
    public String getCurrentLocation() {
        return currentLocation;
    }

    /** The riddle the player needs to solve right now. */
    public String getCurrentRiddle() {
        return route.get(currentStep).riddle;
    }

    /** True if the current riddle is the LAST one (the one that leads to the exam). */
    public boolean isFinalClue() {
        return currentStep == route.size() - 1;
    }

    /**
     * Checks a button click.
     * Returns true if the player clicked the right landmark.
     * If right: the player "walks" there and we move to the next riddle.
     * If wrong: we add one to the wrong guess counter.
     */
    public boolean checkGuess(String clickedLandmark) {
        Landmark correct = route.get(currentStep);

        if (clickedLandmark.equals(correct.name)) {
            currentLocation = correct.name;    // walk to the landmark
            currentStep++;                     // unlock the next riddle
            return true;
        } else {
            wrongGuesses++;
            return false;
        }
    }

    /** True once the player has reached the exam building. */
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