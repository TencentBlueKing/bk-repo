package com.tencent.bkrepo.common.quartz.converter.properties

import com.tencent.bkrepo.common.quartz.converter.TriggerPropertiesConverter
import org.bson.Document
import org.quartz.DateBuilder.IntervalUnit
import org.quartz.impl.triggers.CalendarIntervalTriggerImpl
import org.quartz.spi.OperableTrigger

class CalendarIntervalTriggerPropertiesConverter : TriggerPropertiesConverter() {
    override fun canHandle(trigger: OperableTrigger): Boolean {
        return trigger is CalendarIntervalTriggerImpl && !trigger.hasAdditionalProperties()
    }

    override fun injectExtraPropertiesForInsert(trigger: OperableTrigger, original: Document): Document {
        val calendarIntervalTriggerImpl = trigger as CalendarIntervalTriggerImpl
        return Document(original)
            .append(TRIGGER_REPEAT_INTERVAL_UNIT, calendarIntervalTriggerImpl.repeatIntervalUnit.name)
            .append(TRIGGER_REPEAT_INTERVAL, calendarIntervalTriggerImpl.repeatInterval)
            .append(TRIGGER_TIMES_TRIGGERED, calendarIntervalTriggerImpl.timesTriggered)
    }

    override fun setExtraPropertiesAfterInstantiation(trigger: OperableTrigger, stored: Document) {
        val calendarIntervalTriggerImpl = trigger as CalendarIntervalTriggerImpl
        val repeatIntervalUnit: String? = stored.getString(TRIGGER_REPEAT_INTERVAL_UNIT)
        repeatIntervalUnit?.let { calendarIntervalTriggerImpl.repeatIntervalUnit = IntervalUnit.valueOf(it) }
        stored.getInteger(TRIGGER_REPEAT_INTERVAL)?.let { calendarIntervalTriggerImpl.repeatInterval = it }
        stored.getInteger(TRIGGER_TIMES_TRIGGERED)?.let { calendarIntervalTriggerImpl.timesTriggered = it }

    }

    companion object {
        private const val TRIGGER_REPEAT_INTERVAL_UNIT = "repeatIntervalUnit"
        private const val TRIGGER_REPEAT_INTERVAL = "repeatInterval"
        private const val TRIGGER_TIMES_TRIGGERED = "timesTriggered"
    }
}
