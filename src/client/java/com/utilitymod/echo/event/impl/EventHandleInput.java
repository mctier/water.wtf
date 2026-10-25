package com.utilitymod.echo.event.impl;

import com.utilitymod.echo.event.Event;

public class EventHandleInput extends Event {

    public EventHandleInput(Event.Stage stage) {
        super(stage);
    }

    public static class Pre extends EventHandleInput {
        public Pre() {
            super(Event.Stage.PRE);
        }
    }

    public static class Post extends EventHandleInput {
        public Post() {
            super(Event.Stage.POST);
        }
    }
    
    public static class Early extends EventHandleInput {
        public Early() {
            super(Event.Stage.EARLY);
        }
    }
}