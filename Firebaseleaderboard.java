import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FirebaseLeaderboard.java
 * ----------------------------------------------------------
 * Saves scores to Firebase and reads them back for the leaderboard screen.
 *
 * WHY THIS IS SIMPLE: Instead of installing the big Firebase Java SDK (which
 * needs Maven/Gradle and a secret key file), we talk to Firebase over plain
 * HTTPS using Java's built-in HttpClient. Nothing to download!
 *
 * ONE-TIME SETUP:
 *   1. Go to https://console.firebase.google.com  ->  Create a project
 *   2. Left menu: Build -> Realtime Database -> Create Database
 *   3. Pick a United States location and "Start in test mode"
 *   4. Copy the database URL shown at the top of the Data tab
 *      (looks like https://something-default-rtdb.firebaseio.com)
 *      and paste it into DATABASE_URL below.
 *
 * NOTE: Open rules are fine for a hackathon, but lock them down afterwards!
 */
public class FirebaseLeaderboard {

    // TODO: PASTE YOUR FIREBASE DATABASE URL HERE (no slash at the end!)
    private static final String DATABASE_URL = "https://osu-exam-scavenger-hunt-default-rtdb.firebaseio.com";

    // ===================================================================
    // Entry: one row of the leaderboard (a team name, their major, their score)
    // ===================================================================
    public static class Entry {
        public final String teamName;
        public final String major;
        public final int score;

        public Entry(String teamName, String major, int score) {
            this.teamName = teamName;
            this.major = major;
            this.score = score;
        }
    }

    // ===================================================================
    // SAVE a score
    // ===================================================================

    /**
     * Saves one score to the leaderboard.
     * Returns true if Firebase accepted it, false if something went wrong
     * (no internet, wrong URL, rules blocking writes, etc.)
     */
    public static boolean submitScore(String teamName, String major, int score) {
        try {
            // 1. Build the data as JSON text, e.g.
            //    {"teamName":"Buckeyes","major":"CS","score":742,"timestamp":1700000000000}
            String json = "{"
                    + "\"teamName\":\"" + escape(teamName) + "\","
                    + "\"major\":\"" + escape(major) + "\","
                    + "\"score\":" + score + ","
                    + "\"timestamp\":" + System.currentTimeMillis()
                    + "}";

            // 2. POST it to the "leaderboard" folder. Firebase automatically
            //    creates a new unique entry each time we POST.
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DATABASE_URL + "/leaderboard.json"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            // 3. Send the request and wait for the answer
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 200 means "success"
            if (response.statusCode() == 200) {
                return true;
            }
            System.out.println("Firebase error " + response.statusCode() + ": " + response.body());
            return false;

        } catch (Exception e) {
            // Any problem (like no Wi-Fi) ends up here instead of crashing the game
            System.out.println("Could not reach Firebase: " + e.getMessage());
            return false;
        }
    }

    // ===================================================================
    // READ the top scores
    // ===================================================================

    /**
     * Downloads every saved score, sorts them from highest to lowest,
     * and returns the best 'maxEntries' of them.
     *
     * Returns an EMPTY list if nobody has played yet.
     * Returns null if something went wrong (so the screen can show an error).
     */
    public static List<Entry> getTopScores(int maxEntries) {
        try {
            // 1. Ask Firebase for everything in the "leaderboard" folder
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DATABASE_URL + "/leaderboard.json"))
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("Firebase error " + response.statusCode() + ": " + response.body());
                return null;
            }

            // 2. Firebase answers with JSON text that looks like:
            //    {"-Abc123":{"major":"CS","score":742,"teamName":"Buckeyes","timestamp":1700}, "-Def456":{...}}
            //    (or just the word null if the folder is empty).
            //    Instead of installing a JSON library, we hunt for each { ... } chunk
            //    with a "regular expression" and pull the 3 fields out of each chunk.
            String body = response.body();
            List<Entry> entries = new ArrayList<>();
            Matcher chunkFinder = Pattern.compile("\\{[^{}]*\\}").matcher(body);

            while (chunkFinder.find()) {
                String chunk = chunkFinder.group();
                String team = readText(chunk, "teamName");
                String major = readText(chunk, "major");
                int score = readNumber(chunk, "score");
                entries.add(new Entry(team, major, score));
            }

            // 3. Sort so the highest score comes first
            entries.sort((a, b) -> Integer.compare(b.score, a.score));

            // 4. Keep only the top few
            if (entries.size() > maxEntries) {
                entries = new ArrayList<>(entries.subList(0, maxEntries));
            }
            return entries;

        } catch (Exception e) {
            System.out.println("Could not reach Firebase: " + e.getMessage());
            return null;
        }
    }

    // ===================================================================
    // Small helper methods
    // ===================================================================

    /** Pulls a text value out of a JSON chunk, like "teamName":"Buckeyes" -> Buckeyes */
    private static String readText(String chunk, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher matcher = pattern.matcher(chunk);
        if (matcher.find()) {
            // Undo the escaping we did when saving (\" becomes " and \\ becomes \)
            return matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return "?";
    }

    /** Pulls a whole number out of a JSON chunk, like "score":742 -> 742 */
    private static int readNumber(String chunk, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)");
        Matcher matcher = pattern.matcher(chunk);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    /**
     * Makes text safe to put inside JSON quotes.
     * (A team name like:  The "Best" Team  would otherwise break the JSON.)
     */
    private static String escape(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", " ")
                   .replace("\r", " ");
    }
}