package com.utilitymod.echo.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** echo event.EventSubscribe. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventSubscribe {
    Priority priority() default Priority.NORMAL;

    enum Priority { HIGHEST, HIGH, NORMAL, LOW, LOWEST }
}
