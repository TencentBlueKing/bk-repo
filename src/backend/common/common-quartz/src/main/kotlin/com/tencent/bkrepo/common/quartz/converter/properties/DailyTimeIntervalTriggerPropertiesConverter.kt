package com.tencent.bkrepo.common.quartz.converter.properties

import com.tencent.bkrepo.common.quartz.converter.TriggerPropertiesConverter
import org.bson.Document
import org.quartz.DailyTimeIntervalTrigger
import org.quartz.DateBuilder
import org.quartz.TimeOfDay
import org.quartz.impl.triggers.DailyTimeIntervalTriggerImpl
import org.quartz.spi.OperableTrigger
import java.util.ArrayList
import java.util.HashSet

class DailyTimeIntervalTriggerPropertiesConverter: TriggerPropertiesConverter() {
    override fun canHandle(trigger: OperableTrigger): Boolean {
        return trigger is DailyTimeIntervalTrigger && !(trigger as DailyTimeIntervalTriggerImpl).hasAdditionalProperties()
    }

    override fun injectExtraPropertiesForInsert(trigger: OperableTrigger, original: Document): Document {
        val dailyTimeIntervalTriggerImpl = trigger as DailyTimeIntervalTriggerImpl

        return Document(original)
            .append(TRIGGER_REPEAT_INTERVAL_UNIT, dailyTimeIntervalTriggerImpl.repeatIntervalUnit.name)
            .append(TRIGGER_REPEAT_INTERVAL, dailyTimeIntervalTriggerImpl.repeatInterval)
            .append(TRIGGER_TIMES_TRIGGERED, dailyTimeIntervalTriggerImpl.timesTriggered)
            .append(TRIGGER_START_TIME_OF_DAY, toDocument(dailyTimeIntervalTriggerImpl.startTimeOfDay))
            .append(TRIGGER_END_TIME_OF_DAY, toDocument(dailyTimeIntervalTriggerImpl.endTimeOfDay))
            .append(TRIGGER_DAYS_OF_WEEK, ArrayList(dailyTimeIntervalTriggerImpl.daysOfWeek))
    }

    override fun setExtraPropertiesAfterInstantiation(trigger: OperableTrigger, stored: Document) {
        val dailyTimeIntervalTriggerImpl = trigger as DailyTimeIntervalTriggerImpl

        val intervalUnit = stored.getString(TRIGGER_REPEAT_INTERVAL_UNIT)
        if (intervalUnit != null) {
            dailyTimeIntervalTriggerImpl.repeatIntervalUnit = DateBuilder.IntervalUnit.valueOf(intervalUnit)
        }
        stored.getInteger(TRIGGER_REPEAT_INTERVAL)?.let { dailyTimeIntervalTriggerImpl.repeatInterval = it }
        stored.getInteger(TRIGGER_TIMES_TRIGGERED)?.let { dailyTimeIntervalTriggerImpl.timesTriggered = it }

        val startTOD = stored[TRIGGER_START_TIME_OF_DAY] as Document?
        startTOD?.let { dailyTimeIntervalTriggerImpl.startTimeOfDay = fromDocument(it) }

        val endTOD = stored[TRIGGER_END_TIME_OF_DAY] as Document?
        endTOD?.let { dailyTimeIntervalTriggerImpl.endTimeOfDay = fromDocument(it) }

        stored.getList(TRIGGER_DAYS_OF_WEEK, Int::class.java)?.let {
            dailyTimeIntervalTriggerImpl.daysOfWeek = HashSet(it)
        }
    }

    private fun fromDocument(tod: Document): TimeOfDay {
        return TimeOfDay(tod.getInteger("hour"), tod.getInteger("minute"), tod.getInteger("second"))
    }

    private fun toDocument(tod: TimeOfDay): Document? {
        return Document()
            .append("hour", tod.hour)
            .append("minute", tod.minute)
            .append("second", tod.second)
    }

    companion object {
        private const val TRIGGER_REPEAT_INTERVAL_UNIT = "repeatIntervalUnit"
        private const val TRIGGER_REPEAT_INTERVAL = "repeatInterval"
        private const val TRIGGER_TIMES_TRIGGERED = "timesTriggered"
        private const val TRIGGER_START_TIME_OF_DAY = "startTimeOfDay"
        private const val TRIGGER_END_TIME_OF_DAY = "endTimeOfDay"
        private const val TRIGGER_DAYS_OF_WEEK = "daysOfWeek"
    }
}