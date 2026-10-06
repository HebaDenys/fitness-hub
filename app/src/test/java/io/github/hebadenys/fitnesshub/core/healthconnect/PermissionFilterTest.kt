package io.github.hebadenys.fitnesshub.core.healthconnect

import io.github.hebadenys.fitnesshub.core.model.HealthMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Granular permission evaluation. A partial grant must import what was allowed
 * and leave the rest absent, never fail the whole sync.
 */
class PermissionFilterTest {

    private val stepsPermission = "android.permission.health.READ_STEPS"
    private val sleepPermission = "android.permission.health.READ_SLEEP"
    private val weightPermission = "android.permission.health.READ_WEIGHT"
    private val historyPermission = "android.permission.health.READ_HEALTH_DATA_HISTORY"

    private val permissionByMetric = mapOf(
        HealthMetrics.STEPS to stepsPermission,
        HealthMetrics.SLEEP to sleepPermission,
        HealthMetrics.WEIGHT to weightPermission
    )

    @Test
    @DisplayName("no grants yield no metrics, so nothing is imported")
    fun noGrants_noMetrics() {
        assertTrue(PermissionFilter.grantedMetrics(emptySet(), permissionByMetric).isEmpty())
    }

    @Test
    @DisplayName("a partial grant returns exactly the granted metrics")
    fun partialGrant_returnsOnlyGrantedMetrics() {
        val granted = setOf(stepsPermission, sleepPermission)

        val metrics = PermissionFilter.grantedMetrics(granted, permissionByMetric)

        assertEquals(setOf(HealthMetrics.STEPS, HealthMetrics.SLEEP), metrics)
    }

    @Test
    @DisplayName("a single granted metric does not imply the others")
    fun singleGrant_doesNotImplyOthers() {
        val metrics = PermissionFilter.grantedMetrics(setOf(weightPermission), permissionByMetric)

        assertEquals(setOf(HealthMetrics.WEIGHT), metrics)
        assertFalse(HealthMetrics.STEPS in metrics)
    }

    @Test
    @DisplayName("unknown or unrelated permissions never grant a metric")
    fun unrelatedPermissions_ignored() {
        val granted = setOf(
            "android.permission.CAMERA",
            "android.permission.health.READ_EXERCISE",
            "not.a.real.permission"
        )

        assertTrue(PermissionFilter.grantedMetrics(granted, permissionByMetric).isEmpty())
    }

    @Test
    @DisplayName("history access is reported only when explicitly granted")
    fun historyAccess_requiresExplicitGrant() {
        assertTrue(PermissionFilter.isHistoryGranted(setOf(historyPermission), historyPermission))
        assertFalse(PermissionFilter.isHistoryGranted(setOf(stepsPermission), historyPermission))
        assertFalse(PermissionFilter.isHistoryGranted(emptySet(), historyPermission))
    }

    @Test
    @DisplayName("a write permission never satisfies a read requirement")
    fun writePermission_doesNotGrantRead() {
        val granted = setOf("android.permission.health.WRITE_STEPS")

        assertTrue(PermissionFilter.grantedMetrics(granted, permissionByMetric).isEmpty())
    }

    @Test
    @DisplayName("the returned metric set is independent of the caller's input")
    fun returnedSet_isIndependent() {
        val granted = mutableSetOf(stepsPermission)

        val metrics = PermissionFilter.grantedMetrics(granted, permissionByMetric)
        granted += sleepPermission

        assertEquals(setOf(HealthMetrics.STEPS), metrics)
    }
}
