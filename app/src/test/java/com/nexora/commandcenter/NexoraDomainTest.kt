package com.nexora.commandcenter

import com.nexora.commandcenter.domain.ScientificCalculatorUseCase
import com.nexora.commandcenter.domain.TextUtilitiesUseCase
import com.nexora.commandcenter.domain.UnitCategory
import com.nexora.commandcenter.domain.UnitConverterUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NexoraDomainTest {

    private val calculator = ScientificCalculatorUseCase()
    private val converter = UnitConverterUseCase()
    private val textUtils = TextUtilitiesUseCase()

    @Test
    fun `scientific calculator evaluates complex expressions accurately`() {
        val result = calculator.evaluate("sqrt(144) + 2^5 - (10 / 2)")
        assertTrue(result.isSuccess)
        assertEquals(39.0, result.getOrThrow(), 0.0001)
    }

    @Test
    fun `scientific calculator handles trigonometry and modulo`() {
        val result = calculator.evaluate("sin(90) + (17 % 5)")
        assertTrue(result.isSuccess)
        assertEquals(3.0, result.getOrThrow(), 0.0001)
    }

    @Test
    fun `unit converter converts length and temperature accurately`() {
        val kmToMeters = converter.convert(
            value = 2.5,
            category = UnitCategory.LENGTH,
            fromUnit = "Kilometer (km)",
            toUnit = "Meter (m)"
        )
        assertEquals(2500.0, kmToMeters, 0.001)

        val cToF = converter.convert(
            value = 100.0,
            category = UnitCategory.TEMPERATURE,
            fromUnit = "Celsius (°C)",
            toUnit = "Fahrenheit (°F)"
        )
        assertEquals(212.0, cToF, 0.001)
    }

    @Test
    fun `text analyzer computes deterministic SHA-256 and Base64`() {
        val stats = textUtils.analyze("NEXORA")
        assertEquals(6, stats.characters)
        assertEquals(1, stats.words)
        assertEquals("TkVYT1JB", stats.base64Encoded)
        assertEquals(64, stats.sha256Hex.length)
    }

    @Test
    fun `phase 3 chat request dto carries smart memory and provider configuration`() {
        val memory = com.nexora.commandcenter.core.network.MemoryContextItemDto(
            title = "Clean Architecture",
            factContent = "Use Hilt + Room + Retrofit",
            category = "Android"
        )
        val request = com.nexora.commandcenter.core.network.ChatRequestDto(
            message = "Audit repository layer",
            memories = listOf(memory),
            provider = "gemini",
            model = "gemini-2.5-flash"
        )
        assertEquals(1, request.memories.size)
        assertEquals("gemini", request.provider)
        assertEquals("Clean Architecture", request.memories.first().title)
    }
}
