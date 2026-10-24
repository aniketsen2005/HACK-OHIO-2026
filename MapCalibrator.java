import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * MapCalibrator.java
 * ----------------------------------------------------------
 * A tiny helper tool (NOT part of the game itself).
 *
 * It shows campus_map.png at the same size as the game map.
 * Click anywhere on the picture and it prints the x, y spot,
 * both in the window and in the VSCode console.
 *
 * HOW TO USE:
 *   1. Run this file (click "Run" above main).
 *   2. Click where you want each landmark's button to go.
 *   3. Copy those two numbers into that landmark's line in GameLogic.java
 *      (the last two numbers of each "new Landmark(...)").
 *
 * The size below MUST match MAP_WIDTH / MAP_HEIGHT in GameWindow.java.
 */
public class MapCalibrator {

    private static final int MAP_WIDTH = 900;
    private static final int MAP_HEIGHT = 560;

    public static void main(String[] args) {
        // 1. Load the picture
        BufferedImage image = null;
        try {
            image = ImageIO.read(new File("campus_map.png"));
        } catch (Exception e) {
            // image stays null, handled just below
        }
        if (image == null) {
            JOptionPane.showMessageDialog(null,
                    "Could not find campus_map.png in the project's main folder.\n"
                            + "Save your map picture there with exactly that name and try again.");
            return;
        }

        final BufferedImage mapImage = image;                 // "final" so the panel below can use it
        final List<Point> clicks = new ArrayList<>();         // every spot clicked so far
        final JLabel infoLabel = new JLabel("Click on the map to get x, y coordinates", SwingConstants.CENTER);
        infoLabel.setFont(new Font("Arial", Font.BOLD, 16));

        // 2. A panel that draws the picture and a numbered red dot for each click
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(mapImage, 0, 0, MAP_WIDTH, MAP_HEIGHT, this);

                for (int i = 0; i < clicks.size(); i++) {
                    Point p = clicks.get(i);
                    g.setColor(Color.RED);
                    g.fillOval(p.x - 5, p.y - 5, 10, 10);
                    g.setColor(Color.WHITE);
                    g.drawString(String.valueOf(i + 1), p.x + 8, p.y - 6);
                }
            }
        };
        panel.setPreferredSize(new Dimension(MAP_WIDTH, MAP_HEIGHT));

        // 3. When the mouse is clicked, remember the spot and print it
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                clicks.add(new Point(e.getX(), e.getY()));
                String text = "Click #" + clicks.size() + ":   " + e.getX() + ", " + e.getY();
                infoLabel.setText(text);
                System.out.println(text);
                panel.repaint();
            }
        });

        // 4. Show the window
        JFrame frame = new JFrame("Map Calibrator - click to get coordinates");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(infoLabel, BorderLayout.NORTH);
        frame.add(panel, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}