package me.forketyfork.growing;

import me.forketyfork.growing.auctionsniper.ui.MainWindow;

import javax.swing.*;

public class Main {

    private MainWindow ui;

    public Main() throws Exception {
        startUserInterface();
    }

    public static void main(String... args) throws Exception {
        Main main = new Main();
    }

    private void startUserInterface() throws Exception {
        SwingUtilities.invokeAndWait((Runnable) () -> ui = new MainWindow());
    }
}
