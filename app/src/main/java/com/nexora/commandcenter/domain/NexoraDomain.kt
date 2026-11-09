package com.nexora.commandcenter.domain

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Pure Kotlin recursive-descent scientific math expression evaluator.
 * Supports +, -, *, /, %, ^, parentheses, sin, cos, tan, sqrt, log, ln, pi, e.
 * Works 100% offline.
 */
class ScientificCalculatorUseCase @Inject constructor() {

    fun evaluate(expression: String): Result<Double> = runCatching {
        val sanitized = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("π", Math.PI.toString())
            .replace(Regex("\\be\\b"), Math.E.toString())
            .trim()
        require(sanitized.isNotEmpty()) { "Empty expression" }
        Parser(sanitized).parse()
    }

    private class Parser(private val str: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw IllegalArgumentException("Unexpected character: ${str[pos]}")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        require(divisor != 0.0) { "Division by zero" }
                        x /= divisor
                    }
                    eat('%'.code) -> {
                        val divisor = parseFactor()
                        require(divisor != 0.0) { "Modulo by zero" }
                        x %= divisor
                    }
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
                while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDouble()
            } else if (ch >= 'a'.code && ch <= 'z'.code) {
                while (ch >= 'a'.code && ch <= 'z'.code) nextChar()
                val func = str.substring(startPos, pos)
                x = parseFactor()
                x = when (func) {
                    "sqrt" -> sqrt(x)
                    "sin" -> sin(Math.toRadians(x))
                    "cos" -> cos(Math.toRadians(x))
                    "tan" -> tan(Math.toRadians(x))
                    "log" -> log10(x)
                    "ln" -> ln(x)
                    else -> throw IllegalArgumentException("Unknown function: $func")
                }
            } else {
                throw IllegalArgumentException("Unexpected token")
            }

            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }
    }
}

enum class UnitCategory(
    val displayNameEn: String,
    val displayNameFa: String,
    val units: List<String>
) {
    LENGTH(
        "Length",
        "طول",
        listOf("Meter (m)", "Kilometer (km)", "Centimeter (cm)", "Millimeter (mm)", "Mile (mi)", "Foot (ft)", "Inch (in)")
    ),
    MASS(
        "Mass",
        "جرم و وزن",
        listOf("Kilogram (kg)", "Gram (g)", "Milligram (mg)", "Metric Ton (t)", "Pound (lb)", "Ounce (oz)")
    ),
    TEMPERATURE(
        "Temperature",
        "دما",
        listOf("Celsius (°C)", "Fahrenheit (°F)", "Kelvin (K)")
    ),
    DATA(
        "Digital Storage",
        "حجم داده",
        listOf("Kilobyte (KB)", "Megabyte (MB)", "Gigabyte (GB)", "Terabyte (TB)", "Byte (B)")
    ),
    SPEED(
        "Speed",
        "سرعت",
        listOf("Meter/sec (m/s)", "Kilometer/h (km/h)", "Miles/h (mph)", "Knot (kn)")
    )
}

class UnitConverterUseCase @Inject constructor() {
    fun convert(value: Double, category: UnitCategory, fromUnit: String, toUnit: String): Double {
        if (fromUnit == toUnit) return value
        return when (category) {
            UnitCategory.LENGTH -> {
                val meters = when (fromUnit) {
                    "Kilometer (km)" -> value * 1000.0
                    "Centimeter (cm)" -> value / 100.0
                    "Millimeter (mm)" -> value / 1000.0
                    "Mile (mi)" -> value * 1609.344
                    "Foot (ft)" -> value * 0.3048
                    "Inch (in)" -> value * 0.0254
                    else -> value
                }
                when (toUnit) {
                    "Kilometer (km)" -> meters / 1000.0
                    "Centimeter (cm)" -> meters * 100.0
                    "Millimeter (mm)" -> meters * 1000.0
                    "Mile (mi)" -> meters / 1609.344
                    "Foot (ft)" -> meters / 0.3048
                    "Inch (in)" -> meters / 0.0254
                    else -> meters
                }
            }
            UnitCategory.MASS -> {
                val kg = when (fromUnit) {
                    "Gram (g)" -> value / 1000.0
                    "Milligram (mg)" -> value / 1_000_000.0
                    "Metric Ton (t)" -> value * 1000.0
                    "Pound (lb)" -> value * 0.45359237
                    "Ounce (oz)" -> value * 0.02834952
                    else -> value
                }
                when (toUnit) {
                    "Gram (g)" -> kg * 1000.0
                    "Milligram (mg)" -> kg * 1_000_000.0
                    "Metric Ton (t)" -> kg / 1000.0
                    "Pound (lb)" -> kg / 0.45359237
                    "Ounce (oz)" -> kg / 0.02834952
                    else -> kg
                }
            }
            UnitCategory.TEMPERATURE -> {
                val celsius = when (fromUnit) {
                    "Fahrenheit (°F)" -> (value - 32.0) * 5.0 / 9.0
                    "Kelvin (K)" -> value - 273.15
                    else -> value
                }
                when (toUnit) {
                    "Fahrenheit (°F)" -> (celsius * 9.0 / 5.0) + 32.0
                    "Kelvin (K)" -> celsius + 273.15
                    else -> celsius
                }
            }
            UnitCategory.DATA -> {
                val mb = when (fromUnit) {
                    "Byte (B)" -> value / (1024.0 * 1024.0)
                    "Kilobyte (KB)" -> value / 1024.0
                    "Gigabyte (GB)" -> value * 1024.0
                    "Terabyte (TB)" -> value * 1024.0 * 1024.0
                    else -> value
                }
                when (toUnit) {
                    "Byte (B)" -> mb * 1024.0 * 1024.0
                    "Kilobyte (KB)" -> mb * 1024.0
                    "Gigabyte (GB)" -> mb / 1024.0
                    "Terabyte (TB)" -> mb / (1024.0 * 1024.0)
                    else -> mb
                }
            }
            UnitCategory.SPEED -> {
                val mps = when (fromUnit) {
                    "Kilometer/h (km/h)" -> value / 3.6
                    "Miles/h (mph)" -> value * 0.44704
                    "Knot (kn)" -> value * 0.514444
                    else -> value
                }
                when (toUnit) {
                    "Kilometer/h (km/h)" -> mps * 3.6
                    "Miles/h (mph)" -> mps / 0.44704
                    "Knot (kn)" -> mps / 0.514444
                    else -> mps
                }
            }
        }
    }

    fun convertToAllUnits(
        value: Double,
        category: UnitCategory,
        fromUnit: String
    ): List<Pair<String, Double>> {
        return category.units.map { unit ->
            unit to convert(value, category, fromUnit, unit)
        }
    }
}

class QrGeneratorUseCase @Inject constructor() {
    fun generateQrBitmap(
        content: String,
        sizePx: Int = 512,
        foregroundHex: String = "#00E5FF",
        backgroundHex: String = "#070B19"
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints
            )
            val fgColor = runCatching { Color.parseColor(foregroundHex) }.getOrDefault(Color.CYAN)
            val bgColor = runCatching { Color.parseColor(backgroundHex) }.getOrDefault(Color.BLACK)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) fgColor else bgColor)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}

class TextUtilitiesUseCase @Inject constructor() {
    data class TextStats(
        val characters: Int,
        val charactersNoSpaces: Int,
        val words: Int,
        val sentences: Int,
        val lines: Int,
        val estimatedReadSeconds: Int,
        val sha256Hex: String,
        val md5Hex: String,
        val base64Encoded: String,
        val urlEncoded: String
    )

    fun analyze(text: String): TextStats {
        val trimmed = text.trim()
        val words = if (trimmed.isEmpty()) 0 else trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val sentences = if (trimmed.isEmpty()) 0 else trimmed.split(Regex("[.!?؟]+")).filter { it.isNotBlank() }.size
        val lines = if (text.isEmpty()) 0 else text.lines().size
        val readSeconds = ((words / 200.0) * 60.0).toInt().coerceAtLeast(if (words > 0) 1 else 0)
        val bytes = text.toByteArray(Charsets.UTF_8)

        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        val md5 = MessageDigest.getInstance("MD5")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        val base64 = Base64.getEncoder().encodeToString(bytes)
        val urlEnc = runCatching { URLEncoder.encode(text, "UTF-8") }.getOrDefault("")

        return TextStats(
            characters = text.length,
            charactersNoSpaces = text.count { !it.isWhitespace() },
            words = words,
            sentences = sentences,
            lines = lines,
            estimatedReadSeconds = readSeconds,
            sha256Hex = sha256,
            md5Hex = md5,
            base64Encoded = base64,
            urlEncoded = urlEnc
        )
    }

    fun decodeBase64(encoded: String): Result<String> = runCatching {
        String(Base64.getDecoder().decode(encoded.trim()), Charsets.UTF_8)
    }

    fun decodeUrl(encoded: String): Result<String> = runCatching {
        URLDecoder.decode(encoded.trim(), "UTF-8")
    }
}
