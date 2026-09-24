package com.lmt.tooltx

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ForkInstaller {

    const val FORK_PACKAGE = "org.cromite.cromite"

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(FORK_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun archName(): String {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: return "arm64"
        return when {
            abi.startsWith("arm64") -> "arm64"
            abi == "x86_64" || abi == "x64" -> "x64"
            abi.startsWith("arm") -> "arm"
            else -> "arm64"
        }
    }

    fun prepareApk(context: Context, serverUrl: String): File? {
        val dir = File(context.cacheDir, "apk").apply { mkdirs() }
        val arch = archName()
        val out = File(dir, "${arch}_ChromePublic.apk")
        // 1) APK đóng gói sẵn trong assets (nếu có)
        try {
            context.assets.open("${arch}_ChromePublic.apk").use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            if (out.length() > 0) return out
        } catch (_: Exception) {}
        // 2) Cromite official trên GitHub (luôn có bản mới, không phụ thuộc server)
        try {
            val url =
                "https://github.com/uazo/cromite/releases/latest/download/${arch}_ChromePublic.apk"
            java.net.URL(url).openStream().use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            if (out.length() > 0) return out
        } catch (_: Exception) {}
        // 3) APK tự host trên server riêng (build tùy chỉnh)
        try {
            val url = serverUrl.trimEnd('/') + "/${arch}_ChromePublic.apk"
            java.net.URL(url).openStream().use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            if (out.length() > 0) return out
        } catch (_: Exception) {}
        return null
    }

    fun install(context: Context, apk: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}