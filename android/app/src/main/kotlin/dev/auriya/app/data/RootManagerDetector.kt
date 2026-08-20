package dev.auriya.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class RootEnvironmentInfo(
    val hasRoot: Boolean = false,
    val rootName: String = "No Root",
    val rootType: RootType = RootType.NONE,
    val coreVersion: String = "",
    val kernelVersion: String = "",
    val managerPackage: String? = null,
    val managerLabel: String? = null,
    val managerSignatureHash: String? = null,
    val isSpoofed: Boolean = false,
    val isLkm: Boolean = false,
    val managerIcon: ImageBitmap? = null,
)

enum class RootType {
    NONE,
    KERNELSU,
    KERNELSU_NEXT,
    KOWSU,
    SUKISU,
    APATCH,
    MAGISK,
    MAGISK_ALPHA,
    KITSUNE_MASK,
    UNKNOWN,
}

object RootManagerDetector {
    // Known SHA-256 hashes of KernelSU manager signing certificates across upstream & forks
    val KSU_HASH_MAP =
        mapOf(
            "79e590113c4c4c0c222978e413a5faa801666957b1212a328e46c00c69821bf7" to "KernelSU Next / Official",
            "c371061b19d8c7d7d6133c6a9bafe198fa944e50c1b31c9d8daa8d7f1fc2d2d6" to "KernelSU Official (Legacy)",
            "f415f4ed9435427e1fdf7f1fccd4dbc07b3d6b8751e4dbcec6f19671f427870b" to "RKSU Manager (rsuntk)",
            "52d52d8c8bfbe53dc2b6ff1c613184e2c03013e090fe8905d8e3d5dc2658c2e4" to "WKSU Manager (WildKernelSU)",
            "d3469712b6214462764a1d8d3e5cbe1d6819a0b629791b9f4101867821f1df64" to "ReSukiSU Manager",
            "947ae944f3de4ed4c21a7e4f7953ecf351bfa2b36239da37a34111ad29993eef" to "SukiSU Ultra Manager",
            "484fcba6e6c43b1fb09700633bf2fb4758f13cb0b2f4457b80d075084b26c588" to "KnowSU Manager (KOWX712)",
            "7e0c6d7278a3bb8e364e0fcba95afaf3666cf5ff3c245a3b63c8833bd0445cc4" to "MKSU Manager (5ec1cff)",
            "4359c171f32543394cbc23ef908c4bb94cad7c8087002ba164c8230948c21549" to "KernelSU Debug / Dev",
        )

    private val KSU_HASHES = KSU_HASH_MAP.keys

    private val KNOWN_MANAGER_PACKAGES =
        setOf(
            "me.weishu.kernelsu",
            "com.rifsxd.ksunext",
            "org.adithya.ksunext",
            "com.know.kernelsu",
            "me.sukisu.kernelsu",
            "me.sukisu.ultra",
            "com.resukisu.kernelsu",
            "com.topjohnwu.magisk",
            "io.github.vvb2060.magisk",
            "me.bmax.apatch",
        )

    fun getAppSignatureSha256(
        pm: PackageManager,
        packageName: String,
    ): String? =
        try {
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_SIGNATURES
                }
            val packageInfo = pm.getPackageInfo(packageName, flags)
            val signatures =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.signatures
                }
            val cert = signatures?.firstOrNull()?.toByteArray()
            if (cert != null) {
                val md = MessageDigest.getInstance("SHA-256")
                md.digest(cert).joinToString("") { "%02x".format(it) }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }

    suspend fun detect(context: Context): RootEnvironmentInfo =
        withContext(Dispatchers.IO) {
            val hasRoot = RootShell.hasCachedRoot() || RootShell.hasRoot()
            if (!hasRoot) {
                return@withContext RootEnvironmentInfo(hasRoot = false)
            }

            val pm = context.packageManager

            // 1. Detect binary flavor & version via root shell
            val suVer = RootShell.run("su -v 2>/dev/null")
            val ksudHelp = RootShell.run("ksud --help 2>/dev/null | head -n 1")
            val ksudVer = RootShell.run("ksud -V 2>/dev/null")
            val ksudDebug = RootShell.run("ksud debug info 2>/dev/null")
            val magiskVer = RootShell.run("magisk -v 2>/dev/null")
            val apdVer = RootShell.run("apd -V 2>/dev/null")

            val isLkm = ksudDebug.contains("lkm: true", ignoreCase = true)
            val kVer =
                Regex("""Kernel Version:\s*(\d+)""").find(RootShell.run("ksud debug version 2>/dev/null"))?.groupValues?.get(1)
                    ?: Regex("""version:\s*(\d+)""").find(ksudDebug)?.groupValues?.get(1) ?: ""

            val (rootType, rootName, coreVersion) =
                when {
                    ksudHelp.contains("KernelSU Next", ignoreCase = true) -> {
                        Triple(RootType.KERNELSU_NEXT, "KernelSU Next", ksudVer.ifEmpty { suVer })
                    }

                    ksudHelp.contains("KernelSU", ignoreCase = true) || suVer.contains("KernelSU", ignoreCase = true) -> {
                        Triple(RootType.KERNELSU, "KernelSU", ksudVer.ifEmpty { suVer })
                    }

                    ksudHelp.contains("SukiSU", ignoreCase = true) -> {
                        Triple(RootType.SUKISU, "SukiSU", ksudVer.ifEmpty { suVer })
                    }

                    ksudHelp.contains("KnowSU", ignoreCase = true) -> {
                        Triple(RootType.KOWSU, "KnowSU", ksudVer.ifEmpty { suVer })
                    }

                    magiskVer.contains("alpha", ignoreCase = true) || suVer.contains("alpha", ignoreCase = true) -> {
                        Triple(RootType.MAGISK_ALPHA, "Magisk Alpha", magiskVer.ifEmpty { suVer })
                    }

                    magiskVer.contains("delta", ignoreCase = true) || magiskVer.contains("kitsune", ignoreCase = true) -> {
                        Triple(RootType.KITSUNE_MASK, "Kitsune Mask", magiskVer.ifEmpty { suVer })
                    }

                    magiskVer.isNotEmpty() || suVer.contains("MAGISK", ignoreCase = true) -> {
                        Triple(RootType.MAGISK, "Magisk", magiskVer.ifEmpty { suVer })
                    }

                    apdVer.isNotEmpty() || suVer.contains("apatch", ignoreCase = true) -> {
                        Triple(RootType.APATCH, "APatch", apdVer.ifEmpty { suVer })
                    }

                    else -> {
                        Triple(RootType.UNKNOWN, "Root (su)", suVer)
                    }
                }

            // 2. Discover Manager App via SHA-256 Checksum or Package/Intent Search
            var managerPkg: String? = null
            var managerLabel: String? = null
            var isSpoofed = false
            var managerIcon: ImageBitmap? = null

            // Get list of 3rd-party installed applications
            val installedApps =
                pm
                    .getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }

            var detectedForkName: String? = null

            // Method A: Check by SHA-256 Cert Checksum
            for (app in installedApps) {
                val certHash = getAppSignatureSha256(pm, app.packageName)
                if (certHash != null && KSU_HASH_MAP.containsKey(certHash)) {
                    managerPkg = app.packageName
                    detectedForkName = KSU_HASH_MAP[certHash]
                    isSpoofed = !KNOWN_MANAGER_PACKAGES.contains(app.packageName)
                    break
                }
            }

            // Method B: Check by known manager packages if not found
            if (managerPkg == null) {
                for (pkg in KNOWN_MANAGER_PACKAGES) {
                    if (installedApps.any { it.packageName == pkg }) {
                        managerPkg = pkg
                        isSpoofed = false
                        break
                    }
                }
            }

            // Method C: Check by FlashModule / application/zip handler
            if (managerPkg == null) {
                val zipIntent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(android.net.Uri.parse("content://fake/module.zip"), "application/zip")
                    }
                val handlers = pm.queryIntentActivities(zipIntent, 0)
                val match =
                    handlers.firstOrNull { ri ->
                        val p = ri.activityInfo.packageName
                        installedApps.any { it.packageName == p } && (
                            ri.activityInfo.name.contains("Flash", ignoreCase = true) ||
                                ri.activityInfo.name.contains("Module", ignoreCase = true)
                        )
                    }
                if (match != null) {
                    managerPkg = match.activityInfo.packageName
                    isSpoofed = !KNOWN_MANAGER_PACKAGES.contains(managerPkg)
                }
            }

            var managerSignatureHash: String? = null
            // Load Manager App Label, Icon & Signature Hash if found
            if (managerPkg != null) {
                managerSignatureHash = getAppSignatureSha256(pm, managerPkg)
                try {
                    val appInfo = pm.getApplicationInfo(managerPkg, 0)
                    managerLabel = pm.getApplicationLabel(appInfo).toString()
                    val iconDrawable = pm.getApplicationIcon(appInfo)
                    managerIcon = iconDrawable.toBitmap().asImageBitmap()
                } catch (_: Throwable) {
                    // Fallback to null
                }
            }

            RootEnvironmentInfo(
                hasRoot = true,
                rootName = rootName,
                rootType = rootType,
                coreVersion = coreVersion,
                kernelVersion = kVer,
                managerPackage = managerPkg,
                managerLabel = managerLabel,
                managerSignatureHash = managerSignatureHash,
                isSpoofed = isSpoofed,
                isLkm = isLkm,
                managerIcon = managerIcon,
            )
        }
}
