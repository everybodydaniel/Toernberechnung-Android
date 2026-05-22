package com.example.trnberechnung.logic

import org.junit.Test
import java.time.LocalDateTime
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.booleans.shouldBeFalse

class RuleOfTwelfthsTest {

    @Test
    fun `calculateWaterLevel target before start returns heightStart`() {
        val start = LocalDateTime.of(2024, 1, 1, 12, 0)
        val end = start.plusHours(6)
        val target = start.minusHours(1)
        
        val result = RuleOfTwelfths.calculateWaterLevel(start, 0.0, end, 3.0, target)
        result shouldBe 0.0
    }

    @Test
    fun `calculateWaterLevel target after end returns heightEnd`() {
        val start = LocalDateTime.of(2024, 1, 1, 12, 0)
        val end = start.plusHours(6)
        val target = end.plusHours(1)
        
        val result = RuleOfTwelfths.calculateWaterLevel(start, 0.0, end, 3.0, target)
        result shouldBe 3.0
    }

    @Test
    fun `calculateWaterLevel at midpoint of 6h rising tide 0 to 3m is about 1_5m`() {
        val start = LocalDateTime.of(2024, 1, 1, 12, 0)
        val end = start.plusHours(6)
        val target = start.plusHours(3)
        
        val result = RuleOfTwelfths.calculateWaterLevel(start, 0.0, end, 3.0, target)
        result shouldBe (1.5 plusOrMinus 0.1)
    }

    @Test
    fun `calculateWaterLevel at 1_6 phase 1h into 6h is about 0_25m`() {
        val start = LocalDateTime.of(2024, 1, 1, 12, 0)
        val end = start.plusHours(6)
        val target = start.plusHours(1)
        
        val result = RuleOfTwelfths.calculateWaterLevel(start, 0.0, end, 3.0, target)
        result shouldBe (0.25 plusOrMinus 0.1)
    }

    @Test
    fun `calculateWaterLevel zero duration returns heightEnd`() {
        val start = LocalDateTime.of(2024, 1, 1, 12, 0)
        val target = start
        
        val result = RuleOfTwelfths.calculateWaterLevel(start, 0.0, start, 3.0, target)
        result shouldBe 3.0
    }

    @Test
    fun `calculateUKC positive`() {
        val result = RuleOfTwelfths.calculateUKC(2.0, 5.0, 1.5)
        result shouldBe 5.5
    }

    @Test
    fun `calculateUKC negative or low`() {
        val result = RuleOfTwelfths.calculateUKC(0.0, 1.0, 1.5)
        result shouldBe -0.5
    }

    @Test
    fun `evaluateGoNoGo with clear margin`() {
        RuleOfTwelfths.evaluateGoNoGo(1.0, 0.5).shouldBeTrue()
    }

    @Test
    fun `evaluateGoNoGo below margin`() {
        RuleOfTwelfths.evaluateGoNoGo(0.3, 0.5).shouldBeFalse()
    }

    @Test
    fun `evaluateGoNoGo exact margin boundary`() {
        RuleOfTwelfths.evaluateGoNoGo(0.5, 0.5).shouldBeTrue()
    }
}
