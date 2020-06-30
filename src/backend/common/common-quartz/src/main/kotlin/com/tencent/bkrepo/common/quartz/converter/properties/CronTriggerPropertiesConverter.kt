package com.tencent.bkrepo.common.quartz.converter.properties

import com.tencent.bkrepo.common.quartz.converter.TriggerPropertiesConverter
import org.bson.Document
import org.quartz.CronExpression
import org.quartz.CronTrigger

import org.quartz.impl.triggers.CronTriggerImpl

import org.quartz.spi.OperableTrigger
import java.text.ParseException
import java.util.TimeZone

class CronTriggerPropertiesConverter : TriggerPropertiesConverter() {
    override fun canHandle(trigger: OperableTrigger): Boolean {
        return trigger is CronTriggerImpl && !trigger.hasAdditionalProperties()
    }

    override fun injectExtraPropertiesForInsert(trigger: OperableTrigger, original: Document): Document {
        val cronTrigger = trigger as CronTrigger
        return Document(original)
            .append(TRIGGER_CRON_EXPRESSION, cronTrigger.cronExpression)
            .append(TRIGGER_TIMEZONE, cronTrigger.timeZone.id)
    }

    override fun setExtraPropertiesAfterInstantiation(trigger: OperableTrigger, stored: Document) {
        val cronTriggerImpl = trigger as CronTriggerImpl
        val expression: String? = stored.getString(TRIGGER_CRON_EXPRESSION)
        if (expression != null) {
            try {
                cronTriggerImpl.setCronExpression(CronExpression(expression))
            } catch (e: ParseException) {
                // no good handling strategy and
                // checked exceptions route sucks just as much.
            }
        }
        stored.getString(TRIGGER_TIMEZONE)?.let { cronTriggerImpl.timeZone = TimeZone.getTimeZone(it) }
    }

    companion object {
        private const val TRIGGER_CRON_EXPRESSION = "cronExpression"
        private const val TRIGGER_TIMEZONE = "timezone"
    }
}
