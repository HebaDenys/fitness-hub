package io.github.hebadenys.fitnesshub.core.xiaomi

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class XiaomiPrivateSigningGateTest {
    private val approved = "A1345938ECF27FB5D87609695C161EF4331529648DABDB8700EA1B51C1DF3D13"
    @Test fun approvedNonDebuggablePublisherPasses() {
        assertTrue(XiaomiPrivateSigningGate.permits("io.github.hebadenys.fitnesshub", false, listOf(approved)))
    }
    @Test fun debugMissingWrongAndMultipleSignersFailClosed() {
        assertFalse(XiaomiPrivateSigningGate.permits("io.github.hebadenys.fitnesshub", true, listOf(approved)))
        assertFalse(XiaomiPrivateSigningGate.permits("different.package", false, listOf(approved)))
        assertFalse(XiaomiPrivateSigningGate.permits("io.github.hebadenys.fitnesshub", false, emptyList()))
        assertFalse(XiaomiPrivateSigningGate.permits("io.github.hebadenys.fitnesshub", false, listOf("0".repeat(64))))
        assertFalse(XiaomiPrivateSigningGate.permits("io.github.hebadenys.fitnesshub", false, listOf(approved, approved)))
    }
}
