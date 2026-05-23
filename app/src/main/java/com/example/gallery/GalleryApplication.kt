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
import java.io.PrintWriter
import java.io.StringWriter
import java.security.Security
import org.conscrypt.Conscrypt
import kotlin.system.exitProcess

import com.example.gallery.ui.GalleryState

class GalleryApplication : Application(), ImageLoaderFactory {
    lateinit var galleryState: GalleryState
        private set

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
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
            .allowHardware(false) // GIFやアニメーションの品質向上のため、ハードウェアビットマップを無効化
            .crossfade(false) // アニメーションGIFでの残像（つぶつぶ）を防ぐため無効化
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
