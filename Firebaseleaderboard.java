import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * FirebaseLeaderboard.java
 * ----------------------------------------------------------
 * Sends a team's final score to the Firebase Realtime Database.
 *
 * WHY THIS IS SIMPLE: Instead of installing the big Firebase Java SDK (which
 * needs Maven/Gradle and a secret key file), we talk to Firebase over plain
 * HTTPS using Java's built-in HttpClient. Nothing to download!
 *
 * ONE-TIME SETUP (one teammate does this, then pastes the URL below):
 *   1. Go to https://console.firebase.google.com  ->  Add project
 *   2. Left menu: Build -> Realtime Database -> Create Database
 *   3. Choose "Start in test mode"  (or set Rules to the text below)
 *         { "rules": { ".read": true, ".write": true } }
 *   4. Copy the database URL shown at the top of the Data tab
 *      (looks like https://something-default-rtdb.firebaseio.com)
 *      and paste it into DATABASE_URL below.
 *
 * NOTE: Open rules are fine for a hackathon, but lock them down afterwards!
 */
public class FirebaseLeaderboard {

    // TODO: PASTE YOUR FIREBASE DATABASE URL HERE (no slash at the end!)
    private static final String DATABASE_URL = "https://YOUR-PROJECT-ID-default-rtdb.firebaseio.com";

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