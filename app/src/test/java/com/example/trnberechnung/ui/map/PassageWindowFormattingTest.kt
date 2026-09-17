package com.example.trnberechnung.ui.map

import com.example.trnberechnung.mapplanning.PassageWindow
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime
import org.junit.Test

class PassageWindowFormattingTest {
    @Test
    fun `passage window range includes both full dates across midnight`() {
        val window =
            PassageWindow(
                start = ZonedDateTime.parse("2026-09-17T17:10:00+02:00[Europe/Berlin]"),
                end = ZonedDateTime.parse("2026-09-18T05:00:00+02:00[Europe/Berlin]"),
            )

        window.formatRange() shouldBe "17.09.2026, 17:10 Uhr – 18.09.2026, 05:00 Uhr"
    }
}
