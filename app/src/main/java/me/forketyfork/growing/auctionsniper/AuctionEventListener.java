package me.forketyfork.growing.auctionsniper;

public interface AuctionEventListener {

    void auctionClosed();

    void currentPrice(int price, int increment);
}
