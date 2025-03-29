package com.brunomoyse.tokyosushibar.ui.printing

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sunmi.peripheral.printer.*

data class OrderProductLine(
    val product: Product,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double
)

data class Product(
    val id: String,
    val code: String,
    val name: String,
    val categoryName: String
)

class PrintHandler(private val context: Context) {
    private var service: SunmiPrinterService? = null
    private val TAG = "PrintHandler"

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

            // Ensure printer service is connected
            val printerService = service
            if (printerService == null) {
                Log.e(TAG, "Printer service is not connected")
                return
            }

            // Initialize printer
            printerService.printerInit(printCallback)

            // Print main header (only once)
            printerService.printText(formatHeader(), printCallback)

            // Group order lines by product category
            val grouped = orderLines.groupBy { it.product.categoryName }
            var grandTotal = 0.0

            for ((category, lines) in grouped) {
                // Print category header centered with stars
                printerService.printText(createCategoryHeader(category, 32), printCallback)

                // Sort products by code: first by letters, then by number.
                val sortedLines = lines.sortedWith(compareBy(
                    {
                        val regex = Regex("([A-Za-z]+)")
                        regex.find(it.product.code)?.groupValues?.get(1) ?: it.product.code
                    },
                    {
                        val regex = Regex("[A-Za-z]+([0-9]+)")
                        regex.find(it.product.code)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    }
                ))

                for (line in sortedLines) {
                    val row = formatProductLine(
                        code = line.product.code,
                        name = line.product.name,
                        quantity = line.quantity,
                        price = line.totalPrice
                    )
                    printerService.printText(row, printCallback)
                    grandTotal += line.totalPrice
                }
                // Extra line feed after each category group
                printerService.lineWrap(1, printCallback)
            }

            // Print footer with properly aligned total
            printerService.printText(formatFooter(grandTotal), printCallback)

            // Feed extra lines and cut paper
            printerService.lineWrap(3, printCallback)
            printerService.cutPaper(printCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON or printing: ${e.message}")
        }
    }

    private fun formatHeader(): String {
        return formatRow("Code", "Nom", "Qté", "Prix") +
                "--------------------------------\n"
    }

    private fun formatFooter(total: Double): String {
        val formattedTotal = String.format("%7.2f", total).replace('.', ',')
        return "--------------------------------\n" +
                String.format("%-25s%s\n", "TOTAL:", formattedTotal)
    }

    private fun formatProductLine(code: String, name: String, quantity: Int, price: Double): String {
        return formatRow(
            code = code,
            name = name.take(16), // Limit name to 16 chars
            qty = quantity.toString(),
            price = String.format("%.2f", price).replace('.', ',')
        )
    }

    private fun formatRow(code: String, name: String, qty: String, price: String): String {
        // Fixed column widths: Code:5, Nom:16, Qté:4, Prix:7 (total 32 chars)
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