package io.github.hebadenys.fitnesshub.core.xiaomi

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.security.MessageDigest

/** Publisher trust gate, not a Xiaomi protocol signature. No credentials or network calls. */
internal class XiaomiPrivateSigningGate(private val context: Context) : XiaomiNetworkGate {
    override fun requireAllowed() {
        val allowed = try {
            val app = context.applicationInfo
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val certificates = info.signingInfo?.apkContentsSigners.orEmpty().map {
                MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
            }
            permits(context.packageName, app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0, certificates)
        } catch (_: Exception) { false }
        if (!allowed) accessFailure(XiaomiAccessFailure.PRIVATE_SIGNING_REQUIRED)
    }

    companion object {
        private const val APPROVED_CERTIFICATE = "a1345938ecf27fb5d87609695c161ef4331529648dabdb8700ea1b51c1df3d13"
        internal fun permits(packageName: String, debuggable: Boolean, certificates: List<String>): Boolean =
            packageName == "io.github.hebadenys.fitnesshub" && !debuggable &&
                certificates.size == 1 && certificates.single().equals(APPROVED_CERTIFICATE, ignoreCase = true)
    }
}
