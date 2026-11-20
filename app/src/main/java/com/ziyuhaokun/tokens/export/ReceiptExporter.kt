package com.ziyuhaokun.tokens.export

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 小票图片的保存与分享。 */
object ReceiptExporter {
    /** Android 9 及以下写相册需要 WRITE_EXTERNAL_STORAGE（Manifest 里 maxSdkVersion=28）。 */
    val needsLegacyWritePermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** 相册里的目标子目录。 */
    private const val GALLERY_DIR = "Tokens"

    /** 分享用的缓存子目录，跟 res/xml/receipt_paths.xml 的 cache-path 一致。 */
    private const val SHARE_DIR = "receipts"

    /** 存到相册 Pictures/Tokens/。成功返回图片 Uri。 */
    suspend fun saveToGallery(context: Context, image: ImageBitmap, fileName: String): Result<Uri> =
        runCatching {
            withContext(Dispatchers.IO) {
                val bytes = image.toPngBytes()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    insertIntoMediaStore(context, bytes, fileName)
                } else {
                    writeToPublicPictures(context, bytes, fileName)
                }
            }
        }.rethrowCancellation()

    /** 写到缓存目录，通过 FileProvider 调起系统分享面板。 */
    suspend fun share(context: Context, image: ImageBitmap, fileName: String): Result<Unit> =
        runCatching {
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, SHARE_DIR)
                // 每次分享前清掉旧图，避免缓存目录越攒越大。
                dir.listFiles()?.forEach { it.delete() }
                dir.mkdirs()
                File(dir, fileName).also { it.writeBytes(image.toPngBytes()) }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.$SHARE_DIR", file)
            withContext(Dispatchers.Main) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(send, null).apply {
                    // 非 Activity 的 Context（如 Application）起 Activity 必须带 NEW_TASK。
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        }.rethrowCancellation()

    /** ImageBitmap 转 PNG 字节。调用方负责切到 IO 线程。 */
    private fun ImageBitmap.toPngBytes(): ByteArray {
        val bitmap: Bitmap = asAndroidBitmap()
        return ByteArrayOutputStream().use { out ->
            // 无损 PNG，quality 参数会被忽略，写 100 只是表意。
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }

    /** Android 10+：走 MediaStore，不需要存储权限。 */
    private fun insertIntoMediaStore(context: Context, bytes: ByteArray, fileName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + GALLERY_DIR,
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore 插入失败")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("MediaStore 打开输出流失败")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            // 半成品留在相册里会变成一张打不开的图，失败就删掉。
            resolver.delete(uri, null, null)
            throw t
        }
        return uri
    }

    /** Android 9 及以下：直接写公共 Pictures 目录，再通知媒体库扫描。 */
    @Suppress("DEPRECATION")
    private fun writeToPublicPictures(context: Context, bytes: ByteArray, fileName: String): Uri {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            GALLERY_DIR,
        )
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("无法创建相册目录：${dir.absolutePath}")
        }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        return Uri.fromFile(file)
    }

    /** runCatching 会连取消异常一起吞掉，这里重新抛出去，保证协程能正常取消。 */
    private fun <T> Result<T>.rethrowCancellation(): Result<T> = onFailure {
        if (it is CancellationException) throw it
    }
}
