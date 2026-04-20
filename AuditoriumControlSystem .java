package auditoriumsystem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;

/**
 *
 * @author HP
 */
public class AuditoriumControlSystem extends JFrame {

    // FIX 1: generateSimpleQR now returns a real placeholder image instead of throwing
    private BufferedImage generateSimpleQR(String data) {
        // Pure Java QR-style barcode — no external library needed
        int size = 200;
        int moduleCount = 25; // 25x25 grid
        int moduleSize = size / moduleCount;

        boolean[][] grid = buildQRGrid(data, moduleCount);

        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // White background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, size, size);

        // Draw modules
        g.setColor(Color.BLACK);
        for (int row = 0; row < moduleCount; row++) {
            for (int col = 0; col < moduleCount; col++) {
                if (grid[row][col]) {
                    g.fillRect(col * moduleSize, row * moduleSize, moduleSize, moduleSize);
                }
            }
        }

        // Draw finder patterns (3 corners — classic QR look)
        drawFinder(g, 0, 0, moduleSize);
        drawFinder(g, (moduleCount - 7) * moduleSize, 0, moduleSize);
        drawFinder(g, 0, (moduleCount - 7) * moduleSize, moduleSize);

        g.dispose();
        return img;
    }

    private boolean[][] buildQRGrid(String data, int moduleCount) {
        boolean[][] grid = new boolean[moduleCount][moduleCount];
        // Encode data bytes into grid using a simple hash-based pattern
        byte[] bytes = data.getBytes();
        for (int i = 0; i < bytes.length; i++) {
            int val = bytes[i] & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                int pos = i * 8 + bit;
                int row = (pos / moduleCount) % moduleCount;
                int col = pos % moduleCount;
                // Skip finder pattern zones (corners 7x7)
                if (isFinderZone(row, col, moduleCount)) continue;
                grid[row][col] = ((val >> bit) & 1) == 1;
            }
        }
        // Add timing pattern (alternating row/col 6)
        for (int i = 8; i < moduleCount - 8; i++) {
            grid[6][i] = (i % 2 == 0);
            grid[i][6] = (i % 2 == 0);
        }
        return grid;
    }

    private boolean isFinderZone(int row, int col, int moduleCount) {
        // Top-left
        if (row < 8 && col < 8) return true;
        // Top-right
        if (row < 8 && col >= moduleCount - 8) return true;
        // Bottom-left
        if (row >= moduleCount - 8 && col < 8) return true;
        return false;
    }

    private void drawFinder(Graphics2D g, int x, int y, int ms) {
        // Outer black 7x7
        g.setColor(Color.BLACK);
        g.fillRect(x, y, 7 * ms, 7 * ms);
        // Inner white 5x5
        g.setColor(Color.WHITE);
        g.fillRect(x + ms, y + ms, 5 * ms, 5 * ms);
        // Center black 3x3
        g.setColor(Color.BLACK);
        g.fillRect(x + 2 * ms, y + 2 * ms, 3 * ms, 3 * ms);
    }

    // Model Classes
    static class Seat {
        String number;
        Status status;
        private Object occupant;
        private String bookingId;

        public Object getOccupant() { return occupant; }
        public void setOccupant(Object occupant) { this.occupant = occupant; }
        public String getBookingId() { return bookingId; }
        public void setBookingId(String bookingId) { this.bookingId = bookingId; }

        enum Status { AVAILABLE, BOOKED, OCCUPIED, MAINTENANCE }

        // FIX 2: Removed unused id parameter usage / pointless System.currentTimeMillis() call
        Seat(int id, String number) {
            this.number = number;
            this.status = Status.AVAILABLE;
        }
    }

    static class Auditorium {
        int capacity = 150;
        List<Seat> seats = new ArrayList<>();
        LightingStatus lighting = LightingStatus.FULL_BRIGHT;
        double occupancyRate = 0;

        enum LightingStatus { FULL_BRIGHT, DIMMED, DARK, EMERGENCY }

        Auditorium() {
            for (int i = 1; i <= 150; i++) {
                String num = String.format("R%dC%d", (i - 1) / 15 + 1, ((i - 1) % 15) + 1);
                seats.add(new Seat(i, num));
            }
        }

        int getAvailable() {
            return (int) seats.stream().filter(s -> s.status == Seat.Status.AVAILABLE).count();
        }
    }

    // Data
    private final Auditorium auditorium = new Auditorium();
    private final Random random = new Random();

    // FIX 3: Declare refreshTimer as a field so startAutoRefresh() can reference it
    private javax.swing.Timer refreshTimer;

    private JPanel seatMapPanel;
    private JProgressBar occupancyBar;
    private JLabel statusLabel;
    private JSlider brightnessSlider;
    private JLabel qrLabel;
    private JTextArea analyticsText;

    public AuditoriumControlSystem() {
        initUI();
        startAutoRefresh();
    }

    private void initUI() {
        setTitle("🎭 Auditorium Control System v3.0 - Professional Edition");
        setSize(1400, 900);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(240, 248, 255));

        // === TOP STATUS BAR ===
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setPreferredSize(new Dimension(0, 80));
        topPanel.setBackground(new Color(52, 73, 94));

        statusLabel = new JLabel("🎭 Grand Hall | Available: 150/150 | 0% Occupied", JLabel.CENTER);
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));

        occupancyBar = new JProgressBar(0, 150);
        occupancyBar.setStringPainted(true);
        occupancyBar.setForeground(new Color(46, 204, 113));
        occupancyBar.setBackground(Color.WHITE);

        topPanel.add(statusLabel, BorderLayout.NORTH);
        topPanel.add(occupancyBar, BorderLayout.SOUTH);

        // === MAIN TAB PANEL ===
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 14));
        tabs.addTab("🪑 SEAT MAP", createSeatMapPanel());
        tabs.addTab("📱 BOOKING", createBookingPanel());
        tabs.addTab("💡 LIGHTING", createLightingPanel());
        tabs.addTab("📊 ANALYTICS", createAnalyticsPanel());
        tabs.addTab("🔊 ANNOUNCEMENTS", createAnnouncementPanel());

        // === LAYOUT ===
        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel createSeatMapPanel() {
        seatMapPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                drawSeats(g2);
                g2.dispose();
            }

            private void drawSeats(Graphics2D g2) {
                int seatSize = 35;
                int gap = 6;
                int cols = 15, rows = 10;
                int startX = 50, startY = 50;

                g2.setFont(new Font("Arial", Font.BOLD, 10));

                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < cols; col++) {
                        int index = row * cols + col;
                        if (index < auditorium.seats.size()) {
                            Seat seat = auditorium.seats.get(index);
                            int x = startX + col * (seatSize + gap);
                            int y = startY + row * (seatSize + gap);

                            Color color = switch (seat.status) {
                                case AVAILABLE   -> new Color(39, 174, 96);
                                case BOOKED      -> new Color(243, 156, 18);
                                case OCCUPIED    -> new Color(231, 76, 60);
                                case MAINTENANCE -> new Color(149, 165, 166);
                            };

                            g2.setColor(color);
                            g2.fillRoundRect(x, y, seatSize, seatSize, 12, 12);

                            g2.setColor(Color.WHITE);
                            g2.setStroke(new BasicStroke(2));
                            g2.drawRoundRect(x, y, seatSize, seatSize, 12, 12);

                            g2.setColor(Color.WHITE);
                            g2.drawString(seat.number.substring(1), x + 8, y + 22);
                        }
                    }
                }
            }
        };

        seatMapPanel.setBackground(Color.WHITE);
        seatMapPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = (e.getX() - 50) / 41;
                int row = (e.getY() - 50) / 41;
                int index = row * 15 + col;
                if (index >= 0 && index < auditorium.seats.size()) {
                    showSeatMenu(e.getX(), e.getY(), auditorium.seats.get(index));
                }
                seatMapPanel.repaint();
            }
        });

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.add(seatMapPanel, BorderLayout.CENTER);
        return panel;
    }

    private void showSeatMenu(int x, int y, Seat seat) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(createMenuItem("✅ Mark Available", (ActionEvent e) -> {
            seat.status = Seat.Status.AVAILABLE;
            seat.setOccupant(null);
            JOptionPane.showMessageDialog(AuditoriumControlSystem.this, "Seat " + seat.number + " marked AVAILABLE");
        }));
        menu.add(createMenuItem("👤 Mark Occupied", e -> {
            seat.status = Seat.Status.OCCUPIED;
            seat.setOccupant("Visitor #" + random.nextInt(1000));
        }));
        menu.add(createMenuItem("📅 Mark Booked", (ActionEvent e) -> {
            seat.status = Seat.Status.BOOKED;
            seat.setBookingId("BK" + random.nextInt(10000));
            seat.setOccupant("Customer #" + random.nextInt(1000));
        }));
        menu.add(createMenuItem("🔧 Maintenance", e -> seat.status = Seat.Status.MAINTENANCE));
        menu.show(seatMapPanel, x, y);
    }

    private JMenuItem createMenuItem(String text, ActionListener listener) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(listener);
        item.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        return item;
    }

    private JPanel createBookingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel form = new JPanel(new GridLayout(4, 2, 15, 15));
        form.setBorder(new TitledBorder("📱 QR Booking System"));

        JTextField nameField = new JTextField();
        JTextField seatField = new JTextField();
        JButton qrBtn = new JButton("🎫 Generate QR Code");
        qrLabel = new JLabel("Scan or generate QR for booking", JLabel.CENTER);

        form.add(new JLabel("Name:"));
        form.add(nameField);
        form.add(new JLabel("Seat (R#C#):"));
        form.add(seatField);
        form.add(new JLabel(""));
        form.add(qrBtn);

        qrBtn.addActionListener((ActionEvent e) -> {
            String name1 = nameField.getText().trim();
            String seatNum = seatField.getText().trim();
            if (!name1.isEmpty() && !seatNum.isEmpty()) {
                auditorium.seats.stream()
                        .filter(s -> s.number.equals(seatNum))
                        .findFirst().ifPresent(seat -> {
                            seat.status = Seat.Status.BOOKED;
                            seat.setOccupant(name1);
                            seat.setBookingId("QR" + System.currentTimeMillis());
                            showQRCode(name1 + "|" + seatNum + "|" + LocalDateTime.now());
                            JOptionPane.showMessageDialog(AuditoriumControlSystem.this, "✅ Seat " + seatNum + " Booked!");
                        });
            }
        });

        qrLabel.setPreferredSize(new Dimension(300, 300));
        qrLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        qrLabel.setOpaque(true);
        qrLabel.setBackground(Color.WHITE);

        panel.add(form, BorderLayout.NORTH);
        panel.add(qrLabel, BorderLayout.CENTER);
        return panel;
    }

    private void showQRCode(String data) {
        BufferedImage qr = generateSimpleQR(data);
        qrLabel.setIcon(new ImageIcon(qr));
    }

    private JPanel createLightingPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel brightnessPanel = new JPanel(new BorderLayout());
        brightnessPanel.setBorder(new TitledBorder("💡 Smart Lighting Control"));

        brightnessSlider = new JSlider(0, 100, 100);
        brightnessSlider.setMajorTickSpacing(25);
        brightnessSlider.setPaintTicks(true);
        brightnessSlider.setPaintLabels(true);
        brightnessSlider.addChangeListener(e -> {
            int val = brightnessSlider.getValue();
            if (val > 70) auditorium.lighting = Auditorium.LightingStatus.FULL_BRIGHT;
            else if (val > 30) auditorium.lighting = Auditorium.LightingStatus.DIMMED;
            else auditorium.lighting = Auditorium.LightingStatus.DARK;
        });

        JPanel presets = new JPanel(new GridLayout(1, 4, 10, 10));
        presets.add(createLightBtn("🌞 Full", 100, Color.YELLOW));
        presets.add(createLightBtn("🌙 Dim", 40, Color.ORANGE));
        presets.add(createLightBtn("🌑 Dark", 0, Color.GRAY));
        presets.add(createLightBtn("🚨 Emergency", -1, Color.RED));

        brightnessPanel.add(brightnessSlider, BorderLayout.CENTER);
        brightnessPanel.add(presets, BorderLayout.SOUTH);

        panel.add(brightnessPanel, BorderLayout.NORTH);
        return panel;
    }

    private JButton createLightBtn(String text, int brightness, Color color) {
        JButton btn = new JButton(text);
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.addActionListener(e -> {
            if (brightness == -1) {
                JOptionPane.showMessageDialog(this, "🚨 EMERGENCY LIGHTING ACTIVATED!");
                brightnessSlider.setValue(100);
                brightnessSlider.setEnabled(false);
            } else {
                brightnessSlider.setValue(brightness);
                brightnessSlider.setEnabled(true);
            }
        });
        return btn;
    }

    private JPanel createAnalyticsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        analyticsText = new JTextArea();
        analyticsText.setEditable(false);
        analyticsText.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(analyticsText);

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createAnnouncementPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        JTextArea announcementText = new JTextArea("Welcome to Grand Hall!\nPlease find your seat.");
        announcementText.setRows(4);
        announcementText.setFont(new Font("Arial", Font.BOLD, 16));

        JButton playBtn = new JButton("🔊 Play Announcement");
        JButton clearBtn = new JButton("Clear");

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(new JScrollPane(announcementText), gbc);
        gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(playBtn, gbc);
        gbc.gridx = 1;
        panel.add(clearBtn, gbc);

        playBtn.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "🔊 ANNOUNCEMENT PLAYING:\n" +
                    announcementText.getText(), "Audio Playing", JOptionPane.INFORMATION_MESSAGE);
        });

        clearBtn.addActionListener(e -> announcementText.setText(""));

        return panel;
    }

    private void startAutoRefresh() {
        // FIX 3 (continued): refreshTimer is now a declared field, so this compiles correctly
        refreshTimer = new javax.swing.Timer(2000, ex -> {
            updateStatus();
            if (seatMapPanel != null) seatMapPanel.repaint();
            if (analyticsText != null) updateAnalytics();
        });
        refreshTimer.start();
    }

    private void updateStatus() {
        int available = auditorium.getAvailable();
        int occupied = auditorium.capacity - available;
        double rate = (occupied * 100.0) / auditorium.capacity;

        // FIX 4: Store rate back into auditorium.occupancyRate so analytics can use it
        auditorium.occupancyRate = rate;

        statusLabel.setText(String.format("🎭 Grand Hall | Available: %d/%d | %.1f%% Occupied | %s",
                available, auditorium.capacity, rate, auditorium.lighting));

        occupancyBar.setValue(occupied);
        occupancyBar.setString(String.format("Occupied: %d (%.0f%%)", occupied, rate));
    }

    private void updateAnalytics() {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 REAL-TIME ANALYTICS\n");
        sb.append("====================\n");
        sb.append(String.format("Total Capacity: %d\n", auditorium.capacity));
        sb.append(String.format("Available: %d\n", auditorium.getAvailable()));
        sb.append(String.format("Occupied: %d\n", auditorium.capacity - auditorium.getAvailable()));
        sb.append(String.format("Occupancy Rate: %.1f%%\n\n", auditorium.occupancyRate));
        sb.append("🔥 TOP ROWS (Most Occupied):\n");

        for (int r = 1; r <= 10; r++) {
            // FIX 5: Use a final copy of r for use inside the lambda (was null before)
            final int rowNum = r;
            long occupied = auditorium.seats.stream()
                    .filter(s -> s.number.startsWith("R" + rowNum))
                    .filter(s -> s.status == Seat.Status.OCCUPIED)
                    .count();
            if (occupied > 0) {
                sb.append(String.format("Row %d: %d seats\n", r, occupied));
            }
        }
        analyticsText.setText(sb.toString());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new AuditoriumControlSystem().setVisible(true);
            } catch (ClassNotFoundException | IllegalAccessException |
                     InstantiationException | UnsupportedLookAndFeelException ex) {
                System.err.println("Look and feel error: " + ex.getMessage());
            }
        });
    }
}
