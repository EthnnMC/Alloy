package dev.alloy.mixin.fixture;

/** Stands for a game class; its mixin resets the counter at each click. */
public class Counter {

    private int value = 10;

    public int value() {
        return this.value;
    }

    public void click() {
    }
}
