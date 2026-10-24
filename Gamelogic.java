import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * GameLogic.java
 * ----------------------------------------------------------
 * This file holds the "brain" of the game. It does NOT draw anything.
 * It remembers:
 *   - where the player is standing
 *   - which riddle they are on (and whether it is a HARD riddle)
 *   - wrong guesses, hints used, and the streak bonus
 *   - which landmarks they have visited
 *   - how to calculate the score
 *
 * THE STORY: The player has an EXAM in 5 minutes but is lost on campus.
 * Each game picks a RANDOM starting landmark and a RANDOM exam building.
 *
 * THE RULES:
 *   - Solve a few CHECKPOINT riddles; the FINAL riddle leads to the exam building.
 *   - Riddles never run out: every checkpoint is picked at random as you go.
 *   - WRONG guess: -50 points, you get TELEPORTED to a random landmark,
 *     and you must solve a HARDER riddle for the same place.
 *   - HINT: -100 points, shows the first letter of the answer.
 *   - STREAK: right answers in a row earn bonus points.
 *   - If your score reaches 0, you lose!
 *
 * GameWindow.java (the visual part) asks this class questions like
 * "was that click correct?" and "what is the next riddle?".
 */
public class GameLogic {

    // The hard time limit: 5 minutes = 300 seconds.
    // (Want a different length? Change this one number. 60 = 1 minute, 600 = 10 minutes.)
    public static final int TOTAL_SECONDS = 300;

    // Points lost for each wrong guess
    private static final int WRONG_GUESS_PENALTY = 50;

    // Points lost for using a hint
    public static final int HINT_COST = 100;

    // Streak bonus: the 2nd right answer in a row gives 25 points, the 3rd gives 50,
    // the 4th gives 75, and the 5th (and every one after) gives 100.
    private static final int STREAK_BONUS_STEP = 25;
    private static final int STREAK_BONUS_MAX_STEPS = 4;

    // How many checkpoint riddles you must solve BEFORE the final exam riddle.
    // (Wrong guesses never add or remove checkpoints. They just teleport you
    // away and give you a harder riddle for the same place.)
    private static final int CHECKPOINTS_BEFORE_EXAM = 3;

    // What happened after a click. GameWindow uses this to decide what to show.
    public enum Result {
        CORRECT,        // right answer, here comes the next riddle
        WON,            // right answer AND it was the exam building
        WRONG,          // wrong answer: points lost, teleported, harder riddle
        ALREADY_HERE    // the player clicked the place they are already standing at
    }

    // ===================================================================
    // Landmark: a tiny "package" holding everything about ONE place.
    //   name        = text shown on the hover tag
    //   x, y        = where its dot sits on the map (in pixels)
    //   riddle      = the normal clue whose answer is this place
    //   hardRiddle  = the trickier clue used as a punishment after a wrong guess
    // ===================================================================
    public static class Landmark {
        public final String name;
        public final int x;
        public final int y;
        public final String riddle;
        public final String hardRiddle;

        public Landmark(String name, int x, int y, String riddle, String hardRiddle) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.riddle = riddle;
            this.hardRiddle = hardRiddle;
        }
    }

    // ===================================================================
    // ALL THE LANDMARKS.
    //
    // The two numbers right after each name are the dot's x, y spot on the map.
    // (Use MapCalibrator.java to find good spots, then paste them here.)
    //
    // TO ADD A NEW ONE: copy a block, change the name, spot and both riddles.
    // ===================================================================
    public static final Landmark[] LANDMARKS = {
        new Landmark("Dreese Labs",
            630, 175,
            "Computer science and engineering students practically live in my labs and classrooms, especially during finals week. Where am I?",
            "I speak in binary and my halls hum with servers. Two departments, one that writes the code and one that wires the circuits, share my floors. A missing semicolon here can ruin your whole night. Where am I?"),
        new Landmark("Fisher Hall",
            627, 104,
            "Future CEOs, accountants, and marketers fill my classrooms. Business majors in blazers call me home. Where am I?",
            "A famous philanthropist's name sits on the business college that I house. Inside, students argue about supply chains, case studies, and who forgot to do the group project. Where am I?"),
        new Landmark("Knowlton Hall",
            616, 146,
            "Architecture students build models and pull all-nighters in my design studios and classrooms. Where am I?",
            "Foam core, balsa wood, and no sleep: three design programs (architecture, landscape architecture, and city planning) share my roof, and my own walls are a lesson in modern design. Where am I?"),
        new Landmark("Psychology Building",
            614, 310,
            "Minds are studied here! Psychology classes and research labs fill my halls. Where am I?",
            "I study the mind, and the mind studies me. Walk into my lecture halls and you may find hundreds of students learning exactly why they procrastinate. Where am I?"),
        new Landmark("Denney Hall",
            716, 223,
            "English majors and poetry lovers fill my classrooms, and the English department makes its home in me. Where am I?",
            "I am named for an English professor, and my halls are full of sonnets, essays, and red pen marks. If your major demands a thesis statement, you have probably been inside me. Where am I?"),
        new Landmark("Hagerty Hall",
            775, 305,
            "Spanish, Japanese, Arabic, and more: students come to my classrooms to learn languages from around the world. Where am I?",
            "Bonjour, hola, konnichiwa, marhaba! Many tongues are taught under my roof, and my name honors someone who served this university long ago. Where am I?"),
        new Landmark("Enarson Classroom Bldg",
            603, 192,
            "I'm named for a university president, and my name says exactly what I'm for: big lecture classrooms. Where am I?",
            "I honor a past university president, and I hold no labs, no gym, and no museum. I hold rows of seats, whiteboards, and big lecture crowds. Where am I?"),
        new Landmark("Journalism Building",
            649, 203,
            "Future reporters learn here, and the student newspaper, The Lantern, is put together inside me. Where am I?",
            "Deadline! Somewhere in my halls, student reporters turn breaking news into tomorrow's paper. My name is the job itself. Where am I?"),
        new Landmark("McPherson Chemical Lab",
            720, 187,
            "Bubbling beakers and organic chemistry exams are my specialty. Where am I?",
            "My nameplate honors a chemistry professor, and my halls have seen more titrations than anyone can count. Safety goggles are the dress code. Where am I?"),
        new Landmark("Sullivant Hall",
            809, 278,
            "Dance classes meet in my studios, and a museum full of cartoon art is archived inside me. Where am I?",
            "I teach feet to fly and hands to draw. Upstairs they pirouette, and downstairs the museum door bears the name of a Columbus newspaper cartoonist. Where am I?"),
        new Landmark("Page Hall",
            788, 292,
            "A famous astronaut and senator gave his name to the public affairs college that calls me home. Where am I?",
            "The first American to orbit the Earth gave his name to the college inside me, where future leaders study policy and public service. My own name is something you turn in a book. Where am I?")
    };

    // ---- Game state (things that change while playing) ----
    private final Random random = new Random();
    private Landmark exam;                  // the secret exam building (the final destination)
    private Landmark target;                // the place the CURRENT riddle points to
    private String currentLocation;         // name of the landmark the player is standing at
    private Set<String> visited = new HashSet<>();   // names of every landmark the player has stood at

    private int checkpointsSolved = 0;      // how many checkpoint riddles are done
    private boolean hardMode = false;       // true after a wrong guess (harder riddle showing)
    private boolean hintShown = false;      // true if a hint was used on the current riddle
    private boolean finished = false;       // true once the player reached the exam

    private int wrongGuesses = 0;
    private int hintsUsed = 0;
    private int streak = 0;                 // right answers in a row
    private int bonusPoints = 0;            // total streak bonus earned
    private int lastBonus = 0;              // bonus from the most recent right answer

    /**
     * Constructor: runs once when a new game starts.
     * Randomly picks the starting spot and the exam building.
     */
    public GameLogic() {
        // 1. Shuffle all the landmarks. The 1st is where the player spawns,
        //    the 2nd is the secret EXAM building (so they are never the same).
        List<Landmark> shuffled = new ArrayList<>();
        Collections.addAll(shuffled, LANDMARKS);
        Collections.shuffle(shuffled);

        Landmark start = shuffled.get(0);
        exam = shuffled.get(1);

        currentLocation = start.name;
        visited.add(start.name);

        // 2. Pick the first riddle
        pickNextTarget();
    }

    /**
     * Picks which place the next riddle points to.
     * After enough checkpoints, the exam building is next.
     * Otherwise it is a random landmark, preferring ones the player has NOT visited yet.
     */
    private void pickNextTarget() {
        hardMode = false;
        hintShown = false;

        if (checkpointsSolved >= CHECKPOINTS_BEFORE_EXAM) {
            target = exam;
            return;
        }

        List<Landmark> fresh = new ArrayList<>();   // not visited yet
        List<Landmark> any = new ArrayList<>();     // anything that is legal
        for (Landmark landmark : LANDMARKS) {
            // never pick the exam building early, or the place we are standing at
            if (landmark == exam || landmark.name.equals(currentLocation)) {
                continue;
            }
            any.add(landmark);
            if (!visited.contains(landmark.name)) {
                fresh.add(landmark);
            }
        }

        // If the player has already visited everything, any legal landmark will do
        List<Landmark> pool = fresh.isEmpty() ? any : fresh;
        target = pool.get(random.nextInt(pool.size()));
    }

    /**
     * The punishment: move the player to a random landmark.
     * It can't be the exam building, the answer they were looking for,
     * or the place they are already standing at.
     */
    private void teleport() {
        List<Landmark> options = new ArrayList<>();
        for (Landmark landmark : LANDMARKS) {
            if (landmark == exam || landmark == target || landmark.name.equals(currentLocation)) {
                continue;
            }
            options.add(landmark);
        }
        Landmark destination = options.get(random.nextInt(options.size()));
        currentLocation = destination.name;
        visited.add(destination.name);
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

    /** The story and rules shown in a pop-up when the game begins. */
    public String getStoryIntro() {
        return "You have an EXAM in " + (TOTAL_SECONDS / 60) + " minutes... but you're lost on campus!\n\n"
                + "You are standing at: " + currentLocation + "\n\n"
                + "HOW TO PLAY\n"
                + "- Read the riddle, then click the matching dot on the map to walk there.\n"
                + "- Solve " + CHECKPOINTS_BEFORE_EXAM + " checkpoints. The FINAL riddle leads to your exam building.\n"
                + "- Wrong guess: -" + WRONG_GUESS_PENALTY + " points, you get TELEPORTED away, and the riddle gets HARDER.\n"
                + "- Hint button: -" + HINT_COST + " points, shows the first letter of the answer.\n"
                + "- Right answers in a row earn STREAK bonus points.\n"
                + "- If your score hits 0, you miss your exam and lose!\n\n"
                + "Click OK to start the clock!";
    }

    // ===================================================================
    // The click: was it right?
    // ===================================================================

    /**
     * Checks a click on a landmark and updates the game.
     * See the Result list at the top for what each answer means.
     */
    public Result checkGuess(String clickedLandmark) {
        // Clicking the spot you're already standing on does nothing (and costs nothing)
        if (clickedLandmark.equals(currentLocation)) {
            return Result.ALREADY_HERE;
        }

        if (clickedLandmark.equals(target.name)) {
            // ----- RIGHT ANSWER -----
            currentLocation = target.name;      // walk to the landmark
            visited.add(currentLocation);

            // Streak bonus: starts on the 2nd right answer in a row
            streak++;
            lastBonus = 0;
            if (streak >= 2) {
                int steps = Math.min(streak - 1, STREAK_BONUS_MAX_STEPS);
                lastBonus = steps * STREAK_BONUS_STEP;
                bonusPoints += lastBonus;
            }

            // Did they reach the exam?
            if (target == exam) {
                finished = true;
                return Result.WON;
            }

            checkpointsSolved++;
            pickNextTarget();                   // unlock the next riddle
            return Result.CORRECT;
        }

        // ----- WRONG ANSWER: lose points, break the streak, get teleported, harder riddle -----
        wrongGuesses++;
        streak = 0;
        lastBonus = 0;
        teleport();
        hardMode = true;
        return Result.WRONG;
    }

    // ===================================================================
    // Hints
    // ===================================================================

    /** A hint can be used once per riddle, and only if the player can afford it. */
    public boolean canUseHint(int secondsRemaining) {
        return !hintShown && !finished && calculateScore(secondsRemaining) > HINT_COST;
    }

    /** Uses up a hint (costs points). */
    public void useHint() {
        hintShown = true;
        hintsUsed++;
    }

    /** The hint text: the first letter of the answer and how many words it has. */
    public String getHintText() {
        String name = target.name;
        int wordCount = name.split(" ").length;
        return "the answer starts with \"" + name.charAt(0) + "\" and has "
                + wordCount + (wordCount == 1 ? " word." : " words.");
    }

    // ===================================================================
    // Things GameWindow asks about
    // ===================================================================

    /** Where the player is standing right now. */
    public String getCurrentLocation() {
        return currentLocation;
    }

    /** The riddle the player needs to solve right now (harder after a wrong guess). */
    public String getCurrentRiddle() {
        return hardMode ? target.hardRiddle : target.riddle;
    }

    public boolean isHardMode() {
        return hardMode;
    }

    public boolean isHintShown() {
        return hintShown;
    }

    /** True if the current riddle is the LAST one (the one that leads to the exam). */
    public boolean isFinalClue() {
        return target == exam;
    }

    /** Which checkpoint are we on? (1-based, for showing "Checkpoint 2 of 3"). */
    public int getCheckpointNumber() {
        return checkpointsSolved + 1;
    }

    public int getCheckpointTotal() {
        return CHECKPOINTS_BEFORE_EXAM;
    }

    /** True once the player has reached the exam building. */
    public boolean isFinished() {
        return finished;
    }

    /** True if the player has stood at this landmark before (the dot turns gray). */
    public boolean isVisited(String landmarkName) {
        return visited.contains(landmarkName);
    }

    public int getWrongGuesses() {
        return wrongGuesses;
    }

    public int getStreak() {
        return streak;
    }

    /** The bonus earned by the most recent right answer (0 if none). */
    public int getLastBonus() {
        return lastBonus;
    }

    /**
     * Score formula:
     *   Score = 1000 * (Time Remaining / TOTAL_SECONDS)
     *           - (Wrong Guesses * 50)
     *           - (Hints Used * 100)
     *           + (Streak Bonus)
     * The score never goes below 0. At 0, the player loses.
     */
    public int calculateScore(int secondsRemaining) {
        double timeScore = 1000.0 * ((double) Math.max(secondsRemaining, 0) / TOTAL_SECONDS);
        double score = timeScore
                - (wrongGuesses * WRONG_GUESS_PENALTY)
                - (hintsUsed * HINT_COST)
                + bonusPoints;
        if (score < 0) {
            score = 0;
        }
        return (int) Math.round(score);
    }

    // ===================================================================
    // END-OF-GAME MESSAGES
    // When the player reaches the exam, the message depends on their score.
    // TO CHANGE A MESSAGE or a score range: edit the lines in SCORE_TIERS below.
    // ===================================================================

    // One score range: "if your score is at least minScore, you get this title and message"
    private static class ScoreTier {
        final int minScore;
        final String title;
        final String message;

        ScoreTier(int minScore, String title, String message) {
            this.minScore = minScore;
            this.title = title;
            this.message = message;
        }
    }

    // Listed from the HIGHEST score to the LOWEST. The first tier whose minScore
    // is at or below the player's score is the one that gets shown.
    private static final ScoreTier[] SCORE_TIERS = {
        new ScoreTier(1000, "PERFECT!",
            "You were the FIRST one in the room, and the professor asked YOU to hand out the exams. Legendary!"),
        new ScoreTier(900, "WELL PREPARED!",
            "You made it to your exam and you were well prepared! Pencils sharpened, nerves calm."),
        new ScoreTier(800, "EARLY BIRD!",
            "You arrived early and grabbed the perfect seat. Great planning!"),
        new ScoreTier(600, "RIGHT ON TIME!",
            "You walked in just as the exams were being handed out. Solid work, no stress!"),
        new ScoreTier(400, "CUTTING IT CLOSE!",
            "You sat down just as the proctor said \"begin.\" A little more speed and you'd be golden."),
        new ScoreTier(200, "PHEW, THAT WAS TIGHT!",
            "You slid into your seat out of breath with seconds to spare. Maybe study the campus map first!"),
        new ScoreTier(0, "ALMOST LATE!",
            "You almost missed your exam! The door was closing behind you. Prepare better next time!")
    };

    /** The short headline for a score, like "WELL PREPARED!". */
    public static String getRankTitle(int score) {
        return findTier(score).title;
    }

    /** The full message for a score, like "You made it to your exam and you were well prepared!". */
    public static String getEndMessage(int score) {
        return findTier(score).message;
    }

    /** Finds the tier a score belongs to. */
    private static ScoreTier findTier(int score) {
        for (ScoreTier tier : SCORE_TIERS) {
            if (score >= tier.minScore) {
                return tier;
            }
        }
        // (only happens for a negative score, which can't really occur)
        return SCORE_TIERS[SCORE_TIERS.length - 1];
    }
}