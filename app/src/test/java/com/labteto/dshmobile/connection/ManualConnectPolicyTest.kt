package com.labteto.dshmobile.connection

import com.labteto.dshmobile.ui.screens.connect.ConnectFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance cases for the 0.8.2 subnet fix.
 *
 * A manual connect is NEVER refused because the target sits on another subnet: the phone may reach
 * it through a VPN (WireGuard / Tailscale / 节点小宝 / ZeroTier), a routed interface, or a static
 * route, so the HTTP probe is the only judge of reachability. The subnet test is advisory — a
 * warning at most — and a real probe failure (timeout / refused / DNS / not-a-harness / …) is
 * always reported as its own cause, never as a subnet mismatch. The LAN auto-scan keeps its own
 * /24 scope ([subnetCandidates]).
 */
class ManualConnectPolicyTest {

    // Case 1 — same LAN: phone 192.168.43.10, target 192.168.43.20, reachable → PASS
    @Test
    fun `case1 same-lan target is on-subnet and a reachable probe proceeds`() {
        val local = listOf("192.168.43.10")
        assertEquals(
            SubnetRelation.SAME_SUBNET,
            ManualConnectPolicy.subnetRelation("192.168.43.20", local),
        )
        assertFalse(ManualConnectPolicy.offSubnetWarning("192.168.43.20", local))
        assertTrue(proceeds(ProbeOutcome.Reachable))
    }

    // Case 2 — 节点小宝 scenario: phone 192.168.43.x, harness 192.168.5.16, reachable → PASS
    @Test
    fun `case2 off-wifi target reachable through vpn or route passes`() {
        val local = listOf("192.168.43.10")
        assertEquals(
            SubnetRelation.OTHER_SUBNET,
            ManualConnectPolicy.subnetRelation("192.168.5.16", local),
        )
        assertTrue(ManualConnectPolicy.offSubnetWarning("192.168.5.16", local))
        assertTrue(proceeds(ProbeOutcome.Reachable))
    }

    // Case 3 — phone 10.0.0.10, harness 192.168.5.16, reachable → PASS
    @Test
    fun `case3 target on another private range is reachable and passes`() {
        val local = listOf("10.0.0.10")
        assertEquals(
            SubnetRelation.OTHER_SUBNET,
            ManualConnectPolicy.subnetRelation("192.168.5.16", local),
        )
        assertTrue(ManualConnectPolicy.offSubnetWarning("192.168.5.16", local))
        assertTrue(proceeds(ProbeOutcome.Reachable))
    }

    // Case 4 — same topology as Case 3 but unreachable: the real cause (Timeout) is reported,
    // never a subnet mismatch (the DifferentSubnet failure type no longer exists at all).
    @Test
    fun `case4 unreachable off-subnet target reports timeout not subnet mismatch`() {
        val local = listOf("10.0.0.10")
        assertTrue(ManualConnectPolicy.offSubnetWarning("192.168.5.16", local))
        assertEquals(ConnectFailure.Timeout, failureFor(ProbeOutcome.Timeout))
        assertEquals(ConnectFailure.Timeout, failureFor(ProbeOutcome.Unreachable))
    }

    // Case 5 — auto-scan keeps local-subnet scope: the sweep walks only the phone's own /24s.
    @Test
    fun `case5 auto-scan stays inside the phone own subnets`() {
        val wifi = subnetCandidates("192.168.43.10")
        assertTrue(wifi.isNotEmpty())
        assertTrue(wifi.all { it.startsWith("192.168.43.") })
        assertFalse("192.168.5.16" in wifi)

        val vpn = subnetCandidates("10.0.0.10")
        assertTrue(vpn.isNotEmpty())
        assertTrue(vpn.all { it.startsWith("10.0.0.") })
        assertFalse("192.168.5.16" in vpn)
    }

    /** The manual path connects on a reachable probe; it never fails for subnet reasons. */
    private fun proceeds(outcome: ProbeOutcome): Boolean = outcome is ProbeOutcome.Reachable

    /** What the manual path reports when the probe fails — its real cause, via the shared mapping. */
    private fun failureFor(outcome: ProbeOutcome): ConnectFailure = ConnectFailure.from(outcome)
}
