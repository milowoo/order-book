package com.orderbook.strategy;

/**
 * Extension of Strategy for strategies that need cleanup (e.g. scheduled tasks, subscriptions).
 */
public interface StoppableStrategy extends Strategy {

    /** Stop/cleanup the strategy. Called on application shutdown. */
    void stop();
}
