package com.brunomoyse.tokyosushibar.ui.sound

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.webkit.JavascriptInterface

class SoundHandler(private val context: Context) {
    @JavascriptInterface
    fun playNotificationSound() {
        val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context, notification).play()
    }
}