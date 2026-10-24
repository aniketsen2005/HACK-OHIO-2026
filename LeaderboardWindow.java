import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.List;

/**
 * LeaderboardWindow.java
 * ----------------------------------------------------------
 * A small window that shows the TOP 10 scores from Firebase.
 *
 * Two ways it gets used:
 *   1. GameWindow opens it (the "View Leaderboard" button, and after a win).
 *   2. You can run THIS file on its own (click Run above main) to test
 *      your Firebase connection or to show the leaderboard during the demo.
 */
public class LeaderboardWindow extends JFrame {

    private DefaultTableModel tableModel;   // holds the rows of the table
    private JLabel statusLabel;             // shows "Loading..." or errors
    private JButton refreshButton;

    public LeaderboardWindow() {
        setTitle("OSU Scavenger Hunt - Global Leaderboard");
        // DISPOSE_ON_CLOSE closes just this window (not the whole game)
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        // ----- TOP: title -----
        JLabel title = new JLabel("TOP 10 TEAMS", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 24));
        title.setForeground(new Color(187, 0, 0));   // scarlet
        add(title, BorderLayout.NORTH);

        // ----- CENTER: the table -----
        String[] columnNames = {"Rank", "Team", "Major", "Score"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;   // players can't type into the table
            }
        };
        JTable table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setFont(new Font("Arial", Font.PLAIN, 16));
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 16));
        add(new JScrollPane(table), BorderLayout.CENTER);

        // ----- BOTTOM: refresh button and status message -----
        JPanel bottomPanel = new JPanel(new BorderLayout());
        refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadScores());
        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        bottomPanel.add(refreshButton, BorderLayout.WEST);
        bottomPanel.add(statusLabel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        setSize(580, 480);
        setLocationRelativeTo(null);

        loadScores();   // fill the table as soon as the window is created
    }

    /**
     * Downloads the scores from Firebase and puts them in the table.
     * Downloading can be slow, so it runs on a separate thread
     * (otherwise the window would freeze while waiting).
     */
    private void loadScores() {
        refreshButton.setEnabled(false);
        statusLabel.setText("Loading scores...");

        new Thread(() -> {
            // This line talks to the internet
            List<FirebaseLeaderboard.Entry> entries = FirebaseLeaderboard.getTopScores(10);

            // Screen changes must happen on the screen thread, so we hop back with invokeLater
            SwingUtilities.invokeLater(() -> {
                tableModel.setRowCount(0);   // clear old rows

                if (entries == null) {
                    statusLabel.setText("Could not load scores. Check your internet and the Firebase URL.");
                } else if (entries.isEmpty()) {
                    statusLabel.setText("No scores yet. Be the first to finish!");
                } else {
                    for (int i = 0; i < entries.size(); i++) {
                        FirebaseLeaderboard.Entry entry = entries.get(i);
                        tableModel.addRow(new Object[] {i + 1, entry.teamName, entry.major, entry.score});
                    }
                    statusLabel.setText("Showing the top " + entries.size() + " scores.");
                }
                refreshButton.setEnabled(true);
            });
        }).start();
    }

    /** Opens a leaderboard window (used by GameWindow). */
    public static void showWindow() {
        SwingUtilities.invokeLater(() -> new LeaderboardWindow().setVisible(true));
    }

    /** Lets you run this file by itself to test Firebase or to demo the leaderboard. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LeaderboardWindow window = new LeaderboardWindow();
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);   // closing ends the program
            window.setVisible(true);
        });
    }
}