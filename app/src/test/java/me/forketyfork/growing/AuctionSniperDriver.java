package me.forketyfork.growing;

import com.objogate.wl.swing.AWTEventQueueProber;
import com.objogate.wl.swing.driver.JFrameDriver;
import com.objogate.wl.swing.driver.JLabelDriver;
import com.objogate.wl.swing.gesture.GesturePerformer;
import me.forketyfork.growing.auctionsniper.ui.MainWindow;

import static org.hamcrest.Matchers.equalTo;

public class AuctionSniperDriver extends JFrameDriver {

    @SuppressWarnings("unchecked")  // WindowLicker API uses varargs Matcher parameters
    public AuctionSniperDriver(int timeoutMillis) {
        super(new GesturePerformer(),
                JFrameDriver.topLevelFrame(named(MainWindow.MAIN_WINDOW_NAME), showingOnScreen()),
                new AWTEventQueueProber(timeoutMillis, 100));
    }

    @SuppressWarnings("unchecked")  // WindowLicker API uses varargs Matcher parameters
    public void showsSniperStatus(String statusText) {
        new JLabelDriver(this, named(MainWindow.SNIPER_STATUS_NAME)).hasText(equalTo(statusText));
    }

}
