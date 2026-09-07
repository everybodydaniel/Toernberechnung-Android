package com.example.trnberechnung.logic

import io.kotest.matchers.shouldBe
import java.time.ZoneId
import org.junit.Test

class TideTimesTest {
    @Test
    fun `explicit UTC tide timestamps are converted to Berlin time`() {
        val time = TideTimes.parseZDT("2026-07-29T12:00:00Z")

        time?.zone shouldBe ZoneId.of("Europe/Berlin")
        time?.hour shouldBe 14
        time?.minute shouldBe 0
    }

    @Test
    fun `plain BSH local timestamps remain Berlin local time`() {
        val time = TideTimes.parseZDT("2026-07-29 12:00:00")

        time?.hour shouldBe 12
        time?.minute shouldBe 0
    }
}
