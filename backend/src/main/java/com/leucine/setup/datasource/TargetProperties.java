package com.leucine.setup.datasource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tunables for how we reach target Postgres instances. Defaults are chosen for the
 * common case — a bastion a few hundred ms away, one operator driving the UI — where
 * the win comes from paying the SSH handshake and the Postgres auth once rather than
 * per request.
 */
@ConfigurationProperties(prefix = "setup.target")
public record TargetProperties(

    /** How long to wait on the SSH handshake before giving up. */
    @DefaultValue("10s") Duration sshConnectTimeout,

    /** SSH-level keepalive; keeps the bastion from dropping an idle tunnel. */
    @DefaultValue("30s") Duration sshKeepAlive,

    /** Consecutive missed keepalives before the session is declared dead. */
    @DefaultValue("3") int sshKeepAliveCountMax,

    /** Close a tunnel after this long with no request touching it. */
    @DefaultValue("30m") Duration tunnelIdleTimeout,

    /** Prefer AES-NI-accelerated / modern SSH algorithms (falls back to JSch defaults). */
    @DefaultValue("true") boolean sshFastAlgorithms,

    /** Max concurrent physical Postgres connections per target. */
    @DefaultValue("6") int poolMaxSize,

    /** Physical connections kept warm per target, so the next request pays nothing. */
    @DefaultValue("1") int poolMinIdle,

    /** Give up acquiring a pooled connection after this long. */
    @DefaultValue("15s") Duration poolAcquireTimeout,

    /** Drop an idle pooled connection after this long (min-idle is still kept). */
    @DefaultValue("5m") Duration poolIdleTimeout,

    /** Recycle a physical connection after this long. */
    @DefaultValue("20m") Duration poolMaxLifetime,

    /** Ping idle pooled connections this often so dead sockets are found before a query does. */
    @DefaultValue("60s") Duration poolKeepAlive,

    /** TCP connect timeout for Postgres. */
    @DefaultValue("10s") Duration dbConnectTimeout,

    /** Socket read timeout for Postgres — the cap on a single slow statement. */
    @DefaultValue("60s") Duration dbSocketTimeout
) {
}
