package com.labteto.dshmobile.connection

/**
 * How a target address relates to this phone's own subnets.
 */
enum class SubnetRelation {
    /** Shares a /24 with the phone — the ordinary same-LAN case. */
    SAME_SUBNET,

    /**
     * A different private range (or another LAN behind a router). Reachable only if the phone has a
     * route to it — a VPN (WireGuard/Tailscale/节点小宝/ZeroTier), a routed interface, or a static
     * route. Whether such a route exists is a fact only the probe can establish.
     */
    OTHER_SUBNET,

    /** Not an IPv4 literal, or the phone has no IPv4 of its own — nothing to compare. */
    NOT_APPLICABLE,
}

/**
 * The subnet policy for the manual connect path.
 *
 * Deliberately the inverse of a gate: a manual connect is NEVER refused because the target is on
 * another subnet. The phone may reach it through a VPN, a routed interface, or a relay, so the only
 * judge of reachability is the HTTP probe itself. The subnet test is advisory — it produces a
 * warning at most, and the probe's real result (timeout, refused, DNS, not-a-harness, …) is always
 * reported as its own cause, never as a subnet mismatch.
 *
 * The LAN auto-scan keeps its own stricter scope ([DiscoveryEngine.scan] only walks the phone's
 * own /24s): sweeping 254 addresses on a range the phone has no route to would burn timeouts for
 * nothing. Manual connect is a single named target, so the same reasoning does not apply.
 */
object ManualConnectPolicy {

    /**
     * Classify [host] against the phone's own IPv4 addresses.
     *
     * [localIps] may be any of the device's non-loopback IPv4s (Wi-Fi, VPN interface, …) — matching
     * any of them counts, since a VPN interface already proves the route exists.
     */
    fun subnetRelation(host: String, localIps: List<String>): SubnetRelation {
        val parts = host.split('.')
        val isLiteral = parts.size == 4 &&
            parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
        return when {
            !isLiteral || localIps.isEmpty() -> SubnetRelation.NOT_APPLICABLE
            sameSubnet(host, localIps) -> SubnetRelation.SAME_SUBNET
            else -> SubnetRelation.OTHER_SUBNET
        }
    }

    /**
     * Whether the UI should warn that [host] is off this phone's current Wi-Fi subnet.
     *
     * A warning only — the connect attempt still proceeds and the probe decides.
     */
    fun offSubnetWarning(host: String, localIps: List<String>): Boolean =
        subnetRelation(host, localIps) == SubnetRelation.OTHER_SUBNET
}
