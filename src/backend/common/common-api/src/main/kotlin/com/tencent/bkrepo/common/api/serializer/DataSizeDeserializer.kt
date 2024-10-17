package com.tencent.bkrepo.common.api.serializer

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import org.springframework.util.unit.DataSize

class DataSizeDeserializer : StdDeserializer<DataSize>(DataSize::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): DataSize? {
        return if (p.text != null) DataSize.parse(p.text) else null
    }
}
