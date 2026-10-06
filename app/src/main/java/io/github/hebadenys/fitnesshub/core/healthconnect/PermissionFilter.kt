package io.github.hebadenys.fitnesshub.core.healthconnect

/**
 * Pure permission evaluation: turns a set of granted Android permission strings
 * into the set of health metrics the app may import. Partial grants yield
 * partial metric sets — there is never an all-or-nothing check.
 */
object PermissionFilter {

    /** Metrics whose read permission is present in [grantedPermissions]. */
    fun grantedMetrics(
        grantedPermissions: Set<String>,
        permissionByMetric: Map<String, String>
    ): Set<String> = permissionByMetric.filterValues(grantedPermissions::contains).keys

    /** Whether the optional history permission (records older than 30 days) is granted. */
    fun isHistoryGranted(grantedPermissions: Set<String>, historyPermission: String): Boolean =
        historyPermission in grantedPermissions
}
