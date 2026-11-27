package me.mykindos.betterpvp.network.protocol;

import java.util.regex.Pattern;

/**
 * The NATS subject a bus topic travels on: {@code <prefix>.bus.<topic>}, for example {@code bpvp.bus.chat.private}.
 * <p>
 * A topic is one or more dot-separated tokens of letters, digits, {@code _} and {@code -}. The prefix keeps networks
 * that share a NATS cluster apart.
 */
public final class Subjects {

    public static final String DEFAULT_PREFIX = "bpvp";

    private static final Pattern TOPIC = Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*");

    private Subjects() {
    }

    public static String bus(String prefix, String topic) {
        if (!isValidTopic(topic)) {
            throw new IllegalArgumentException("'" + topic + "' cannot be used as a bus topic");
        }
        return prefix + ".bus." + topic;
    }

    /** Everything on the bus, for a service that wants to see all of it. */
    public static String allBus(String prefix) {
        return prefix + ".bus.>";
    }

    public static boolean isValidTopic(String topic) {
        return topic != null && TOPIC.matcher(topic).matches();
    }
}
