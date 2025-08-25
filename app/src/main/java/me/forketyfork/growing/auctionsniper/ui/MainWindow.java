package me.forketyfork.growing.auctionsniper.ui;

import javax.swing.*;

public class MainWindow extends JFrame {
    public static final String STATUS_JOINING = "Joining";
    public static final String STATUS_LOST = "Lost";
    public static final String SNIPER_STATUS_NAME = "Status";
    public static final String MAIN_WINDOW_NAME = "Auction Sniper Main";

    public MainWindow() {
        super("Auction Sniper");
        setName(MAIN_WINDOW_NAME);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }
}
