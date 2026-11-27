package me.mykindos.betterpvp.core.framework.role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The server roles a feature installs on. Put it on a listener, command, adapter or leaderboard, or on a
 * {@code package-info.java} to cover the package and every package below it. The nearest declaration wins, so a
 * subpackage or class can narrow or widen what its parent says. Undeclared features run on every role, and a server
 * with role {@link ServerRole#ALL} installs everything.
 */
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RunsOn {

    ServerRole[] value();

}
