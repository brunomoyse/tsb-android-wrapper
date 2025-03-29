package com.brunomoyse.tokyosushibar

import android.content.Context
import android.os.RemoteException
import android.util.Log
import android.webkit.JavascriptInterface
import com.sunmi.peripheral.printer.*

class PrintHandler(private val context: Context) {

    private var service: SunmiPrinterService? = null
    private val callback = object : InnerPrinterCallback() {
        override fun onConnected(service: SunmiPrinterService?) {
            this@PrintHandler.service = service
            Log.d("PrintHandler", "✅ Printer service connected")
        }

        override fun onDisconnected() {
            Log.d("PrintHandler", "⚠️ Printer service disconnected")
            service = null
        }
    }

    private val printCallback = object : InnerResultCallback() {
        override fun onRunResult(isSuccess: Boolean) {
            Log.d("PrintHandler", "✅ Run result: $isSuccess")
        }

        override fun onReturnString(result: String?) {
            Log.d("PrintHandler", "🔁 Return string: $result")
        }

        override fun onRaiseException(code: Int, msg: String?) {
            Log.e("PrintHandler", "❌ Exception: $code - $msg")
        }

        override fun onPrintResult(code: Int, msg: String?) {
            Log.d("PrintHandler", "🖨️ Print result: $code - $msg")
        }
    }

    init {
        try {
            InnerPrinterManager.getInstance().bindService(context.applicationContext, callback)
            Log.d("PrintHandler", "🔗 bindService called")
        } catch (e: Exception) {
            Log.e("PrintHandler", "❌ Error binding printer service: ${e.message}")
        }
    }

    @JavascriptInterface
    fun print(content: String) {
        Log.d("PrintHandler", "🚀 print() called with content: \"$content\"")

        if (content.isBlank()) {
            Log.e("PrintHandler", "❌ Empty content, not printing.")
            return
        }

        try {
            service?.apply {
                printerInit(printCallback)
                setAlignment(0, printCallback) // Centered
                printText("$content\n", printCallback)
                lineWrap(3, printCallback)
            } ?: Log.e("PrintHandler", "❌ Printer service is null")
        } catch (e: RemoteException) {
            Log.e("PrintHandler", "❌ Print failed: ${e.message}")
        }
    }

    fun cleanup() {
        InnerPrinterManager.getInstance().unBindService(context.applicationContext, callback)
    }
}