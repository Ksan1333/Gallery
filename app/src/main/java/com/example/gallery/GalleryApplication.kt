package com.example.gallery

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class GalleryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
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
