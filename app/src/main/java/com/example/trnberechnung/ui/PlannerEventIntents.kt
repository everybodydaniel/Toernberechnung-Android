package com.example.trnberechnung.ui

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.example.trnberechnung.model.PlannerCalendarData
import com.example.trnberechnung.model.PlannerEvent
import com.example.trnberechnung.model.toCalendarData
import com.example.trnberechnung.model.toShareText
import java.time.ZoneId

internal fun plannerShareIntent(
    event: PlannerEvent,
    participantNames: List<String>,
): Intent =
    Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, event.toShareText(participantNames))

internal fun plannerCalendarIntent(
    event: PlannerEvent,
    participantNames: List<String>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Intent? = event.toCalendarData(zoneId, participantNames)?.toCalendarInsertIntent()

internal fun Context.canHandlePlannerIntent(intent: Intent): Boolean =
    intent.resolveActivity(packageManager) != null

private fun PlannerCalendarData.toCalendarInsertIntent(): Intent =
    Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startEpochMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endEpochMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
        .putExtra(CalendarContract.Events.EVENT_TIMEZONE, timeZoneId)
        .apply {
            location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
        }
