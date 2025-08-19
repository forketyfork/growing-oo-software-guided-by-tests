package me.forketyfork.growing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jxmpp.stringprep.XmppStringprepException;

public class AuctionSniperEndToEndTest {

    private final FakeAuctionServer auction = new FakeAuctionServer("item-54321");
    private final ApplicationRunner application = new ApplicationRunner();

    public AuctionSniperEndToEndTest() throws XmppStringprepException {
    }

    @Test
    public void sniperJoinsAuctionUntilAuctionCloses() throws Exception {
        auction.startSellingItem();
        application.startBiddingIn(auction);
        // TODO enable after implementing the UI
        // auction.hasReceivedJoinRequestFromSniper();
        auction.announceClosed();
        // TODO enable after implementing the UI
        // application.showsSniperHasLostAuction();
    }

    // Additional cleanup
    @AfterEach
    public void stopAuction() {
        auction.stop();
    }

    @AfterEach
    public void stopApplication() {
        application.stop();
    }

}
