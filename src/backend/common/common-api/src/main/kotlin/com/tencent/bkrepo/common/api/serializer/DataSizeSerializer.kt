package com.tencent.bkrepo.common.api.serializer

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.springframework.util.unit.DataSize

class DataSizeSerializer() : StdSerializer<DataSize>(DataSize::class.java) {
    override fun serialize(value: DataSize?, gen: JsonGenerator?, provider: SerializerProvider?) {
        if (gen != null && value != null) {
            gen.writeString(value.toMegabytes().toString() + "MB")
        }
    }
}
