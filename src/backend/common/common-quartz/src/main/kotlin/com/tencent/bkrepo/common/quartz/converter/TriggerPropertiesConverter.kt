package com.tencent.bkrepo.common.quartz.converter

import com.tencent.bkrepo.common.quartz.converter.properties.CalendarIntervalTriggerPropertiesConverter
import com.tencent.bkrepo.common.quartz.converter.properties.CronTriggerPropertiesConverter
import com.tencent.bkrepo.common.quartz.converter.properties.DailyTimeIntervalTriggerPropertiesConverter
import com.tencent.bkrepo.common.quartz.converter.properties.SimpleTriggerPropertiesConverter
import org.bson.Document
import org.quartz.spi.OperableTrigger

abstract class TriggerPropertiesConverter {
    protected abstract fun canHandle(trigger: OperableTrigger): Boolean
    abstract fun injectExtraPropertiesForInsert(trigger: OperableTrigger, original: Document): Document
    abstract fun setExtraPropertiesAfterInstantiation(trigger: OperableTrigger, stored: Document)

    companion object {
        private val propertiesConverters: List<TriggerPropertiesConverter> = listOf(
            SimpleTriggerPropertiesConverter(),
            CalendarIntervalTriggerPropertiesConverter(),
            CronTriggerPropertiesConverter(),
            DailyTimeIntervalTriggerPropertiesConverter()
        )

        /**
         * Returns properties converter for given trigger or null when not found.
         * @param trigger    a trigger instance
         * @return converter or null
         */
        fun getConverterFor(trigger: OperableTrigger): TriggerPropertiesConverter {
            for (converter in propertiesConverters) {
                if (converter.canHandle(trigger)) {
                    return converter
                }
            }
            throw RuntimeException("No proper trigger properties converter")
        }
    }
}
