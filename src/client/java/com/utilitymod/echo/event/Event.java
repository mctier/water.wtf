package com.utilitymod.echo.event;

/** echo event.Event. */
public abstract class Event {
    public final Stage stage;
    public boolean cancelled;

    protected Event(Stage stage) {
        this.stage = stage;
        cancelled = false;
    }

    protected Event() {
        this(Stage.NONE);
    }

    public boolean call() {
        return EventManager.post(this);
    }

    public final boolean isEarly() { return stage == Stage.EARLY; }
    public final boolean isPre() { return stage == Stage.PRE; }
    public final boolean isPost() { return stage == Stage.POST; }

    public final void cancel() {
        cancelled = true;
    }

    public enum Stage { NONE, EARLY, PRE, POST }
}
