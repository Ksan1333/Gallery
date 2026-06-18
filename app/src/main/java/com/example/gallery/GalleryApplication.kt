package com.example.gallery

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.PrintWriter
import java.io.StringWriter
import java.security.Security
import org.conscrypt.Conscrypt
import com.example.gallery.ui.state.GalleryState

class GalleryApplication : Application(), ImageLoaderFactory {
    lateinit var galleryState: GalleryState
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun newImageLoader(): ImageLoader {
        val imageDispatcher = Dispatchers.IO.limitedParallelism(2)

        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.1) // 2%から10%に拡大
                    .build()
            }
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(VideoFrameDecoder.Factory())
            }
            .allowRgb565(true) // デコード速度を向上させメモリ消費を半分に
            .dispatcher(imageDispatcher)
            .interceptorDispatcher(imageDispatcher) // IOスレッドで実行
            .allowHardware(true) // グリッド表示を高速化（ビューワーは個別設定可能）
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        
        // SSL Handshakeエラー（TLS/SNI）対策としてConscryptをセキュリティプロバイダーの先頭に追加
        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        galleryState = GalleryState(this)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()
                
                val deviceInfo = """
                    Device: ${Build.MANUFACTURER} ${Build.MODEL}
                    Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
                    App Version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}
                """.trimIndent()
                
                val body = "Device Info:\n$deviceInfo\n\nStack Trace:\n$stackTrace"
                
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("exceaz13cazie@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, "Gallery App Crash Report")
                    putExtra(Intent.EXTRA_TEXT, body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("GalleryApplication", "Failed to send crash report", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    private val context get() = this
}
