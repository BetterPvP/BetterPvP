package me.mykindos.betterpvp.sessions;

import me.mykindos.betterpvp.network.protocol.NetworkEnvelope;

/**
 * The bus topics the sessions service answers and the payload fields they carry. Each topic travels on
 * {@code <prefix>.bus.<topic>} as a {@link NetworkEnvelope} request, and the
 * service replies to the request's reply subject.
 * <ul>
 *     <li>{@link #CLAIM}: a server asks to own a player. {@link #WAIT} says how long the service may hold the answer
 *     back while another server still owns them. Answers {@link #GRANTED} with an {@link #EPOCH}, or {@link #HELD}
 *     with the {@link #HOLDER}.</li>
 *     <li>{@link #RELEASE}: the owner gives a player up after saving them. Answers {@link #RELEASED} or
 *     {@link #STALE} when that server no longer owns them.</li>
 *     <li>{@link #HEARTBEAT}: a server keeps every lease it holds alive.</li>
 *     <li>{@link #LOGIN} and {@link #LOGOUT}: a proxy binds an account to itself and frees it. A login answers
 *     {@link #ACCEPTED}, or {@link #REFUSED} while another live proxy has the account. A logout answers {@link #RELEASED}.</li>
 *     <li>{@link #PROXY_HEARTBEAT}: a proxy says it is still up, so its accounts stay bound to it.</li>
 * </ul>
 */
public final class SessionProtocol {

    public static final String CLAIM = "session.claim";
    public static final String RELEASE = "session.release";
    public static final String HEARTBEAT = "session.heartbeat";
    public static final String LOGIN = "session.login";
    public static final String LOGOUT = "session.logout";
    public static final String PROXY_HEARTBEAT = "session.proxy-heartbeat";

    public static final String PLAYER = "player";
    public static final String SERVER = "server";
    public static final String PROXY = "proxy";
    public static final String EPOCH = "epoch";
    public static final String WAIT = "wait";
    public static final String RESULT = "result";
    public static final String HOLDER = "holder";

    public static final String GRANTED = "granted";
    public static final String HELD = "held";
    public static final String RELEASED = "released";
    public static final String STALE = "stale";
    public static final String ACCEPTED = "accepted";
    public static final String REFUSED = "refused";

    /**
     * The low bits of a save version count saves within one lease. The high bits are the lease's epoch, so every save
     * made under a newer lease outranks every save made under an older one.
     */
    public static final int SAVES_PER_EPOCH_BITS = 20;

    private SessionProtocol() {
    }

    /** The first save version a server may use under a lease with this epoch. */
    public static long firstVersion(long epoch) {
        return epoch << SAVES_PER_EPOCH_BITS;
    }
}
