package com.brunomoyse.tokyosushibar.ui.printing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.util.Log
import android.webkit.JavascriptInterface
import com.brunomoyse.tokyosushibar.R
import com.google.gson.Gson
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// --- Data models ---

enum class OrderType { DELIVERY, PICKUP }

data class Category(
    val id: String,
    val name: String
)

data class Product(
    val id: String,
    val code: String?,
    val name: String,
    val category: Category
)

data class OrderProductLine(
    val product: Product,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double
)

data class Customer(
    val id: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String?
)

data class Payment(
    val status: String
)

data class Address(
    val streetName: String,
    val houseNumber: String,
    val boxNumber: String?,
    val postcode: String,
    val municipalityName: String
)

data class Order(
    val id: String,
    val createdAt: String,
    val preferredReadyTime: String?,
    val type: OrderType,
    val customer: Customer,
    val payment: Payment?,
    val address: Address?,
    val addressExtra: String?,
    val items: List<OrderProductLine>
)

// --- Print handler ---

class PrintHandler(private val context: Context) {
    private var service: SunmiPrinterService? = null
    private val logTag = "PrintHandler"
    private val printerWidthPx = 384
    private val maxChars = 32

    private val printerCallback = object : InnerPrinterCallback() {
        override fun onConnected(service: SunmiPrinterService?) {
            this@PrintHandler.service = service
            Log.d(logTag, "Printer service connected")
        }
        override fun onDisconnected() {
            Log.d(logTag, "Printer service disconnected")
            service = null
        }
    }

    private val printCallback = object : InnerResultCallback() {
        override fun onRunResult(isSuccess: Boolean) {
            Log.d(logTag, "Run result: $isSuccess")
        }
        override fun onReturnString(result: String?) {
            Log.d(logTag, "Return string: $result")
        }
        override fun onRaiseException(code: Int, msg: String?) {
            Log.e(logTag, "Exception: $code – $msg")
        }
        override fun onPrintResult(code: Int, msg: String?) {
            Log.d(logTag, "Print result: $code – $msg")
        }
    }

    init {
        try {
            InnerPrinterManager.getInstance()
                .bindService(context.applicationContext, printerCallback)
            Log.d(logTag, "Binding printer service")
        } catch (e: Exception) {
            Log.e(logTag, "Error binding printer service: ${e.message}")
        }
    }

    @Suppress("unused")
    @JavascriptInterface
    fun print(content: String) {
        Log.d(logTag, "Received print JSON: $content")
        if (content.isBlank()) {
            Log.e(logTag, "Empty content, nothing to print")
            return
        }

        try {
            val order = Gson().fromJson(content, Order::class.java)
            val printer = service ?: run {
                Log.e(logTag, "Printer service is not connected")
                return
            }

            printer.printerInit(printCallback)
            printLogo(printer)

            // --- Order header ---
            // printer.printText("Commande n°${order.id}\n", printCallback)


            printer.printText("Type: ${mapOrderType(order.type)}\n", printCallback)
            val paymentStatus = order.payment?.status ?: "NON PAYÉ"
            printer.printText("Paiement: $paymentStatus\n", printCallback)
            printer.lineWrap(1, printCallback)

            printer.printText("Créé: ${formatDateTime(order.createdAt)}\n", printCallback)
            printer.printText(
                "Souhaité: ${order.preferredReadyTime?.let { formatDateTime(it) } ?: "Dès que possible"}\n",
                printCallback
            )
            printer.lineWrap(1, printCallback)

            val cust = order.customer
            printer.printText("Client: ${cust.firstName} ${cust.lastName}\n", printCallback)
            cust.phoneNumber?.let {
                printer.printText("Téléphone: $it\n", printCallback)
            }

            // --- Address (if any) ---
            order.address?.let { addr ->
                // Label
                printer.printText("Adresse de livraison:\n", printCallback)

                // street + optional box
                val streetLine = buildString {
                    append(addr.streetName)
                    append(" ")
                    append(addr.houseNumber)
                    addr.boxNumber?.takeIf(String::isNotBlank)?.let {
                        append(" / "); append(it)
                    }
                }
                wrapText(streetLine).forEach { printer.printText("$it\n", printCallback) }

                // postcode + city
                val cityLine = "${addr.postcode} ${addr.municipalityName}"
                wrapText(cityLine).forEach { printer.printText("$it\n", printCallback) }

                // now print addressExtra if present
                order.addressExtra
                    ?.takeIf(String::isNotBlank)
                    ?.let { extra ->
                        // maybe label, or just the extra lines:
                        wrapText(extra).forEach { line ->
                            printer.printText("$line\n", printCallback)
                        }
                    }
            }

            printer.lineWrap(2, printCallback)

            // --- Column headers & items ---
            printer.printText(formatHeader(), printCallback)
            var grandTotal = 0.0
            order.items.groupBy { it.product.category.name }
                .forEach { (category, lines) ->
                    printer.printText(createCategoryHeader(category), printCallback)
                    lines.sortedWith(compareBy(
                        { (it.product.code ?: "").substringBeforeLast(" ").lowercase(Locale.getDefault()) },
                        { (it.product.code ?: "").substringAfterLast(" ").toIntOrNull() ?: 0 }
                    )).forEach { line ->
                        printer.printText(
                            formatProductLine(
                                code = line.product.code ?: "",
                                name = line.product.name,
                                quantity = line.quantity,
                                price = line.totalPrice
                            ), printCallback
                        )
                        grandTotal += line.totalPrice
                    }
                    printer.lineWrap(1, printCallback)
                }

            // --- Footer total ---
            printer.printText(formatFooter(grandTotal), printCallback)
            printer.lineWrap(5, printCallback)

            printer.cutPaper(printCallback)

        } catch (e: Exception) {
            Log.e(logTag, "Error parsing JSON or printing: ${e.message}")
        }
    }

    private fun mapOrderType(type: OrderType) = when (type) {
        OrderType.DELIVERY -> "LIVRAISON"
        OrderType.PICKUP   -> "À EMPORTER"
    }

    private fun formatDateTime(iso: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(iso) ?: Date()
            SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale("fr", "BE")).format(date)
        } catch (e: Exception) {
            iso
        }
    }

    private fun wrapText(text: String): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            if (current.isEmpty()) {
                current = word
            } else if (current.length + 1 + word.length <= maxChars) {
                current += " $word"
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun printLogo(printer: SunmiPrinterService) {
        try {
            val scaled = loadAndScaleLogo() ?: return
            val centered = centerBitmap(scaled)
            val centerAlign = byteArrayOf(0x1B, 0x61, 0x01)
            val leftAlign   = byteArrayOf(0x1B, 0x61, 0x00)

            printer.sendRAWData(centerAlign, printCallback)
            printer.printBitmap(centered, printCallback)
            printer.sendRAWData(leftAlign, printCallback)
            printer.lineWrap(2, printCallback)

            scaled.recycle()
            centered.recycle()
        } catch (e: Exception) {
            Log.e(logTag, "Error printing logo", e)
        }
    }

    private fun loadAndScaleLogo(): Bitmap? {
        val resId = R.drawable.app_logo
        if (resId == 0) {
            Log.e(logTag, "Logo resource not found")
            return null
        }
        val original = BitmapFactory.decodeResource(context.resources, resId)
            ?: return null
        val targetWidth = printerWidthPx * 0.5f
        val scale = targetWidth / original.width
        val matrix = Matrix().apply { postScale(scale, scale) }
        return Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            .also { original.recycle() }
    }

    private fun centerBitmap(bmp: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(printerWidthPx, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val left = (printerWidthPx - bmp.width) / 2f
        canvas.drawBitmap(bmp, left, 0f, null)
        return output
    }

    private fun formatHeader(): String =
        formatRow("Code", "Nom", "Qté", "Prix") + "-".repeat(maxChars) + "\n"

    private fun formatFooter(total: Double): String {
        val fmt = String.format(Locale("fr", "BE"), "%7.2f", total).replace('.', ',')
        return "-".repeat(maxChars) + "\n" +
                String.format(Locale("fr", "BE"), "%-25s%s\n", "TOTAL:", fmt)
    }

    private fun formatProductLine(
        code: String,
        name: String,
        quantity: Int,
        price: Double
    ): String {
        val fmtPrice = String.format(Locale("fr", "BE"), "%.2f", price).replace('.', ',')
        return formatRow(code, name.take(16), quantity.toString(), fmtPrice)
    }

    private fun formatRow(code: String, name: String, qty: String, price: String): String =
        String.format("%-5s%-16s%4s%7s\n", code.take(5), name.take(16), qty.take(4), price.take(7))

    private fun createCategoryHeader(category: String): String {
        val avail = maxChars - (category.length + 2)
        val side  = if (avail > 0) avail / 2 else 0
        val extra = if (avail % 2 != 0) 1 else 0
        return "*".repeat(side) + " $category " + "*".repeat(side + extra) + "\n"
    }

    @Suppress("unused")
    fun cleanup() {
        InnerPrinterManager.getInstance()
            .unBindService(context.applicationContext, printerCallback)
    }
}