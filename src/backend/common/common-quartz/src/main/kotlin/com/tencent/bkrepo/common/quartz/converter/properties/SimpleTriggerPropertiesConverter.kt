package com.tencent.bkrepo.common.quartz.converter.properties

import com.tencent.bkrepo.common.quartz.converter.TriggerPropertiesConverter
import org.bson.Document
import org.quartz.SimpleTrigger
import org.quartz.impl.triggers.SimpleTriggerImpl
import org.quartz.spi.OperableTrigger

class SimpleTriggerPropertiesConverter : TriggerPropertiesConverter() {
    override fun canHandle(trigger: OperableTrigger): Boolean {
        return trigger is SimpleTriggerImpl && !trigger.hasAdditionalProperties()
    }

    override fun injectExtraPropertiesForInsert(trigger: OperableTrigger, original: Document): Document {
        val simpleTrigger = trigger as SimpleTrigger
        return Document(original)
            .append(TRIGGER_REPEAT_COUNT, simpleTrigger.repeatCount)
            .append(TRIGGER_REPEAT_INTERVAL, simpleTrigger.repeatInterval)
            .append(TRIGGER_TIMES_TRIGGERED, simpleTrigger.timesTriggered)
    }

    override fun setExtraPropertiesAfterInstantiation(trigger: OperableTrigger, stored: Document) {
        val simpleTriggerImpl = trigger as SimpleTriggerImpl
        stored.getInteger(TRIGGER_REPEAT_COUNT)?.let { simpleTriggerImpl.repeatCount = it }
        stored.getLong(TRIGGER_REPEAT_INTERVAL)?.let { simpleTriggerImpl.repeatInterval = it }
        stored.getInteger(TRIGGER_TIMES_TRIGGERED)?.let { simpleTriggerImpl.timesTriggered = it }
    }

    companion object {
        private const val TRIGGER_REPEAT_COUNT = "repeatCount"
        private const val TRIGGER_REPEAT_INTERVAL = "repeatInterval"
        private const val TRIGGER_TIMES_TRIGGERED = "timesTriggered"
    }
}
