package me.forketyfork.growing.auctionsniper.ui;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;

public class MainWindow extends JFrame {
    public static final String STATUS_JOINING = "Joining";
    public static final String STATUS_LOST = "Lost";
    public static final String STATUS_BIDDING = "Bidding";
    public static final String SNIPER_STATUS_NAME = "Status";
    public static final String MAIN_WINDOW_NAME = "Auction Sniper Main";

    private final JLabel sniperStatus;

    private MainWindow() {
        super("Auction Sniper");
        this.sniperStatus = createLabel(STATUS_JOINING);
    }

    public static MainWindow createAndShow() {
        MainWindow window = new MainWindow();
        window.initialize();
        return window;
    }

    private void initialize() {
        setName(MAIN_WINDOW_NAME);
        add(sniperStatus);
        pack();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    private static JLabel createLabel(String initialText) {
        JLabel result = new JLabel(initialText);
        result.setName(SNIPER_STATUS_NAME);
        result.setBorder(new LineBorder(Color.BLACK));
        return result;
    }

    public void showStatus(String status) {
        sniperStatus.setText(status);
    }
}
