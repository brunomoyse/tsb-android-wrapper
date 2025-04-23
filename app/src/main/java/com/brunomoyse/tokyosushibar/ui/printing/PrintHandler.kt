package com.brunomoyse.tokyosushibar.ui.printing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.webkit.JavascriptInterface
import com.brunomoyse.tokyosushibar.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sunmi.peripheral.printer.*
import java.util.Locale

data class OrderProductLine(
    val product: Product,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double
)

data class Product(
    val id: String,
    val code: String?,
    val name: String,
    val category: Category
)

data class Category(
    val id: String,
    val name: String
)

class PrintHandler(private val context: Context) {
    private var service: SunmiPrinterService? = null
    private val TAG = "PrintHandler"

    // Printer paper width in pixels (384px for 58mm paper)
    private val printerWidthPx = 384

    private val printerCallback = object : InnerPrinterCallback() {
        override fun onConnected(service: SunmiPrinterService?) {
            this@PrintHandler.service = service
            Log.d(TAG, "Printer service connected")
        }

        override fun onDisconnected() {
            Log.d(TAG, "Printer service disconnected")
            service = null
        }
    }

    private val printCallback = object : InnerResultCallback() {
        override fun onRunResult(isSuccess: Boolean) {
            Log.d(TAG, "Run result: $isSuccess")
        }

        override fun onReturnString(result: String?) {
            Log.d(TAG, "Return string: $result")
        }

        override fun onRaiseException(code: Int, msg: String?) {
            Log.e(TAG, "Exception: $code - $msg")
        }

        override fun onPrintResult(code: Int, msg: String?) {
            Log.d(TAG, "Print result: $code - $msg")
        }
    }

    init {
        try {
            InnerPrinterManager.getInstance().bindService(context.applicationContext, printerCallback)
            Log.d(TAG, "Binding printer service")
        } catch (e: Exception) {
            Log.e(TAG, "Error binding printer service: ${e.message}")
        }
    }

    @JavascriptInterface
    fun print(content: String) {
        Log.d(TAG, "Received print JSON: $content")
        if (content.isBlank()) {
            Log.e(TAG, "Empty content, nothing to print")
            return
        }
        try {
            val gson = Gson()
            val listType = object : TypeToken<List<OrderProductLine>>() {}.type
            val orderLines: List<OrderProductLine> = gson.fromJson(content, listType)
            if (orderLines.isEmpty()) {
                Log.e(TAG, "No order lines found in JSON")
                return
            }

            val printerService = service ?: run {
                Log.e(TAG, "Printer service is not connected")
                return
            }

            // Initialize printer
            printerService.printerInit(printCallback)

            // Print logo first (centered)
            printLogo(printerService)

            // Print header
            printerService.printText(formatHeader(), printCallback)

            // Process order lines
            val grouped = orderLines.groupBy { it.product.category.name }
            var grandTotal = 0.0

            for ((category, lines) in grouped) {
                printerService.printText(createCategoryHeader(category, 32), printCallback)

                val sortedLines = lines.sortedWith(compareBy(
                    { (it.product.code ?: "").substringBeforeLast(" ").lowercase() },
                    { (it.product.code ?: "").substringAfterLast(" ").toIntOrNull() ?: 0 }
                ))

                for (line in sortedLines) {
                    val row = formatProductLine(
                        code = line.product.code ?: "",
                        name = line.product.name,
                        quantity = line.quantity,
                        price = line.totalPrice
                    )
                    printerService.printText(row, printCallback)
                    grandTotal += line.totalPrice
                }
                printerService.lineWrap(1, printCallback)
            }

            // Print footer with total
            printerService.printText(formatFooter(grandTotal), printCallback)

            // Finalize
            printerService.lineWrap(3, printCallback)
            printerService.cutPaper(printCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON or printing: ${e.message}")
        }
    }

    private fun printLogo(printerService: SunmiPrinterService) {
        try {
            val logoBitmap = loadAndScaleLogo() ?: return

            // Center alignment using ESC commands
            val centerAlign = byteArrayOf(0x1B, 0x61, 0x01) // ESC a n (n=1 for center)
            val leftAlign = byteArrayOf(0x1B, 0x61, 0x00)  // ESC a n (n=0 for left)

            // Set alignment
            printerService.sendRAWData(centerAlign, printCallback)

            // Print logo
            printerService.printBitmap(logoBitmap, printCallback)

            // Reset alignment
            printerService.sendRAWData(leftAlign, printCallback)
            printerService.lineWrap(2, printCallback)

            logoBitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error printing logo", e)
        }
    }

    private fun loadAndScaleLogo(): Bitmap? {
        return try {
            // Get resources from context
            val resources = context.resources

            // Get resource ID dynamically
            val resId = R.drawable.app_logo

            if (resId == 0) {
                Log.e(TAG, "Logo resource not found")
                return null
            }

            val originalBitmap = BitmapFactory.decodeResource(
                resources,
                resId
            ) ?: return null

            // Scaling logic (same as before)
            val maxWidth = 384f
            val scale = maxWidth / originalBitmap.width
            val matrix = Matrix().apply { postScale(scale, scale) }

            Bitmap.createBitmap(
                originalBitmap,
                0, 0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            ).also {
                originalBitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading logo: ${e.message}")
            null
        }
    }

    private fun formatHeader(): String {
        return formatRow("Code", "Nom", "Qté", "Prix") +
                "--------------------------------\n"
    }

    private fun formatFooter(total: Double): String {
        val formattedTotal = String.format(Locale("fr", "BE"), "%7.2f", total).replace('.', ',')
        return "--------------------------------\n" +
                String.format(Locale("fr", "BE"), "%-25s%s\n", "TOTAL:", formattedTotal)
    }

    private fun formatProductLine(code: String, name: String, quantity: Int, price: Double): String {
        return formatRow(
            code = code,
            name = name.take(16),
            qty = quantity.toString(),
            price = String.format(Locale("fr", "BE"), "%.2f", price).replace('.', ',')
        )
    }

    private fun formatRow(code: String, name: String, qty: String, price: String): String {
        return String.format("%-5s%-16s%4s%7s\n",
            code.take(5),
            name.take(16),
            qty.take(4),
            price.take(7)
        )
    }

    private fun createCategoryHeader(category: String, maxWidth: Int): String {
        val availableWidth = maxWidth - (category.length + 2)
        val starsEachSide = if (availableWidth > 0) availableWidth / 2 else 0
        val extra = if (availableWidth % 2 != 0) 1 else 0
        return "*".repeat(starsEachSide) + " $category " + "*".repeat(starsEachSide + extra) + "\n"
    }

    fun cleanup() {
        InnerPrinterManager.getInstance().unBindService(context.applicationContext, printerCallback)
    }
}