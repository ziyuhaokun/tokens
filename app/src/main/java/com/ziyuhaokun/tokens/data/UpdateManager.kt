package com.ziyuhaokun.tokens.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    /** [total] 未知时为 -1。 */
    data class Downloading(val info: ReleaseInfo, val downloaded: Long, val total: Long) : UpdateState
    data class Ready(val info: ReleaseInfo, val file: File) : UpdateState
    /** [info] 为空 = 检查阶段失败（还没发现新版本）。 */
    data class Failed(val message: String, val info: ReleaseInfo?) : UpdateState
}

enum class InstallResult { STARTED, NEED_PERMISSION, FAILED }

/**
 * 自动更新：从 GitHub Release 检查新版本、下载、校验，再交给系统安装器。
 *
 * - 自动检查最多每 6 小时一次，失败时静默；设置页的手动检查不限频，失败会提示；
 * - 只有用户点「更新」才下载，不在后台偷偷用流量；
 * - 下载完先校验 SHA-256，再核对包名、版本号、签名证书必须和当前 App 一致，才拉起系统安装器；
 * - 做不到静默安装：最后一步一定要用户在系统界面点确认。
 *
 * 状态放在应用级对象里，转屏 / Activity 重建不会丢下载进度。
 */
class UpdateManager(
    context: Context,
    private val api: UpdateApi,
    private val settings: SettingsStore,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** 弹窗是否打开。「稍后」「跳过」会关掉；手动检查到新版本会再打开。 */
    private val _dialogOpen = MutableStateFlow(false)
    val dialogOpen: StateFlow<Boolean> = _dialogOpen.asStateFlow()

    /** 弹窗里的一次性提示（例如「请先允许安装未知应用」）。 */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    val currentVersion: String by lazy {
        runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }

    private val updatesDir: File get() = File(appContext.cacheDir, "updates")

    /** 启动时调用：距上次检查不到 6 小时就不查。失败不打扰用户。 */
    fun autoCheck(now: Long = System.currentTimeMillis()) {
        scope.launch {
            val stored = settings.snapshot()
            if (_state.value !is UpdateState.Idle) return@launch
            if (now - stored.lastUpdateCheck in 0 until AUTO_CHECK_INTERVAL_MS) return@launch
            // 新进程里不会有上一次留下的下载状态，旧的安装包 / 半截文件顺手清掉。
            updatesDir.deleteRecursively()
            check(manual = false)
        }
    }

    /** 设置页的「检查更新」：不限频，失败会显示原因，发现新版本会弹窗。 */
    fun checkNow() {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        scope.launch { check(manual = true) }
    }

    private suspend fun check(manual: Boolean) {
        _state.value = UpdateState.Checking
        try {
            val info = api.fetchLatest()
            settings.saveLastUpdateCheck(System.currentTimeMillis())
            if (info != null && UpdateLogic.isNewer(info.version, currentVersion)) {
                _state.value = UpdateState.Available(info)
                val skipped = settings.snapshot().skippedVersion
                if (manual || info.tag != skipped) _dialogOpen.value = true
            } else {
                _state.value = UpdateState.UpToDate
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = if (manual) {
                UpdateState.Failed(e.message ?: "检查更新失败", null)
            } else {
                UpdateState.Idle
            }
        }
    }

    /** 用户点「更新」/「重试」。 */
    fun startDownload() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> s.info ?: return
            else -> return
        }
        _notice.value = null
        job?.cancel()
        job = scope.launch {
            _state.value = UpdateState.Downloading(info, 0, info.apkSize)
            val dest = File(updatesDir, "update-${safeName(info.version)}.apk")
            try {
                api.download(info, dest) { done, total ->
                    // 只更新「还在下载」的状态：用户刚点了取消时，慢一步的进度回调不能把状态改回去。
                    _state.update { cur ->
                        if (cur is UpdateState.Downloading) UpdateState.Downloading(info, done, total) else cur
                    }
                }
                verifyApk(dest)
                _state.value = UpdateState.Ready(info, dest)
            } catch (e: CancellationException) {
                dest.delete()
                throw e
            } catch (e: UpdateException) {
                dest.delete()
                _state.value = UpdateState.Failed(e.message ?: "更新失败", info)
            } catch (e: Exception) {
                dest.delete()
                _state.value = UpdateState.Failed("更新失败：安装包无法使用", info)
            }
        }
    }

    /** 下载中点「取消」。回到「有新版本」，可以再点更新。 */
    fun cancelDownload() {
        val s = _state.value as? UpdateState.Downloading ?: return
        job?.cancel()
        _state.value = UpdateState.Available(s.info)
    }

    /** 拉起系统安装器。没有「安装未知应用」权限就先带用户去开。 */
    fun install(): InstallResult {
        val ready = _state.value as? UpdateState.Ready ?: return InstallResult.FAILED
        if (!ready.file.isFile) {
            _state.value = UpdateState.Failed("安装包不见了，请重新下载", ready.info)
            return InstallResult.FAILED
        }
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            _notice.value = "请先在系统设置里允许 Tokens 安装应用，然后回来再点一次「安装」。"
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return if (runCatching { appContext.startActivity(intent) }.isSuccess) {
                InstallResult.NEED_PERMISSION
            } else {
                InstallResult.FAILED
            }
        }
        _notice.value = null
        return runCatching {
            val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.receipts", ready.file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            InstallResult.STARTED
        }.getOrElse {
            _notice.value = "无法打开系统安装器。"
            InstallResult.FAILED
        }
    }

    /** 「稍后」：关掉弹窗，状态保留，设置页里还能看到有新版本。 */
    fun dismiss() {
        _dialogOpen.value = false
        _notice.value = null
    }

    /** 重新打开弹窗（设置页点「发现新版本」）。 */
    fun showDialog() {
        if (_state.value is UpdateState.Available || _state.value is UpdateState.Ready ||
            _state.value is UpdateState.Failed || _state.value is UpdateState.Downloading
        ) {
            _dialogOpen.value = true
        }
    }

    /** 「跳过此版本」：记下 tag，之后不再自动弹这个版本。 */
    fun skipThisVersion() {
        val tag = (_state.value as? UpdateState.Available)?.info?.tag
        _dialogOpen.value = false
        if (tag != null) scope.launch { settings.saveSkippedVersion(tag) }
    }

    /**
     * 装之前的最后一道关：包名一致、版本号更高、签名证书与当前 App 完全一致。
     * 系统安装器本来也会拒绝签名不同的更新包，这里提前拦住，给用户一句人话。
     */
    private fun verifyApk(file: File) {
        val pm = appContext.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateException("安装包无法解析")
        if (archive.packageName != appContext.packageName) throw UpdateException("安装包不是 Tokens")
        val installed = pm.getPackageInfo(appContext.packageName, flags)
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            throw UpdateException("安装包的版本不高于当前版本")
        }
        val newCerts = certDigests(archive)
        if (newCerts.isEmpty() || newCerts != certDigests(installed)) {
            throw UpdateException("安装包签名与当前 App 不一致，已拒绝")
        }
    }

    @Suppress("DEPRECATION")
    private fun certDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        } ?: return emptySet()
        return signatures.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun safeName(version: String): String = version.filter { it.isLetterOrDigit() || it == '.' }

    private companion object {
        const val AUTO_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    }
}
