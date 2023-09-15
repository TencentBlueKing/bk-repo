package com.tencent.bkrepo.ddc.serialization

import com.tencent.bkrepo.ddc.utils.beginUniformArray
import com.tencent.bkrepo.ddc.utils.writeBinaryReference
import com.tencent.bkrepo.ddc.utils.writeInteger
import com.tencent.bkrepo.ddc.utils.writeIntegerValue
import com.tencent.bkrepo.ddc.utils.writeNullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import kotlin.experimental.or

class CbWriterTest {
    @Test
    fun emptyObject() {
        val writer = CbWriter()
        writer.beginObject()
        writer.endObject()

        val data = writer.toByteArray()
        assertEquals(2, data.size)
        assertEquals(data[0], CbFieldType.Object.value)
        assertEquals(data[1], 0.toByte())
    }

    @Test
    fun emptyArray() {
        val writer = CbWriter()
        writer.beginArray()
        writer.endArray()

        val data = writer.toByteArray()
        assertEquals(3, data.size)
        assertEquals(data[0], CbFieldType.Array.value)
        assertEquals(data[1], 1.toByte())
        assertEquals(data[2], 0.toByte())
    }

    @Test
    fun objectTest() {
        val writer = CbWriter()
        writer.beginObject()
        writer.writeInteger("a", 1)
        writer.writeInteger("b", 2)
        writer.writeInteger("c", 3)
        writer.endObject()

        val data = writer.toByteArray()
        assertEquals(14, data.size)
        assertEquals(data[0], CbFieldType.Object.value)
        assertEquals(data[1], 12.toByte()) // Length

        assertEquals(data[2], CbFieldType.IntegerPositive.value or CbFieldType.HasFieldName.value)
        assertEquals(data[3], 1.toByte())
        assertEquals(data[4], 'a'.toByte())
        assertEquals(data[5], 1.toByte())

        assertEquals(data[6], CbFieldType.IntegerPositive.value or CbFieldType.HasFieldName.value)
        assertEquals(data[7], 1.toByte())
        assertEquals(data[8], 'b'.toByte())
        assertEquals(data[9], 2.toByte())

        assertEquals(data[10], CbFieldType.IntegerPositive.value or CbFieldType.HasFieldName.value)
        assertEquals(data[11], 1.toByte())
        assertEquals(data[12], 'c'.toByte())
        assertEquals(data[13], 3.toByte())
    }

    @Test
    fun arrayTest() {
        val writer = CbWriter()
        writer.beginArray()
        writer.writeIntegerValue(1)
        writer.writeIntegerValue(2)
        writer.writeIntegerValue(3)
        writer.endArray()

        val data = writer.toByteArray()
        assertEquals(9, data.size)
        assertEquals(data[0], CbFieldType.Array.value)
        assertEquals(data[1], 7.toByte()) // Length
        assertEquals(data[2], 3.toByte()) // Item count
        assertEquals(data[3], CbFieldType.IntegerPositive.value)
        assertEquals(data[4], 1.toByte())
        assertEquals(data[5], CbFieldType.IntegerPositive.value)
        assertEquals(data[6], 2.toByte())
        assertEquals(data[7], CbFieldType.IntegerPositive.value)
        assertEquals(data[8], 3.toByte())
    }

    @Test
    fun uniformArrayTest() {
        val writer = CbWriter()
        writer.beginUniformArray(CbFieldType.IntegerPositive)
        writer.writeIntegerValue(1)
        writer.writeIntegerValue(2)
        writer.writeIntegerValue(3)
        writer.endArray()

        val data = writer.toByteArray()
        assertEquals(7, data.size)
        assertEquals(data[0], CbFieldType.UniformArray.value)
        assertEquals(data[1], 5.toByte()) // Length
        assertEquals(data[2], 3.toByte()) // Item count
        assertEquals(data[3], CbFieldType.IntegerPositive.value)
        assertEquals(data[4], 1.toByte())
        assertEquals(data[5], 2.toByte())
        assertEquals(data[6], 3.toByte())
    }


    @Test
    fun nestedArray() {
        val writer = CbWriter()
        writer.beginObject()
        writer.beginArray("a")
        writer.writeIntegerValue(1)
        writer.endArray()
        writer.endObject()

        val data = writer.toByteArray()
        assertEquals(9, data.size)
        assertEquals(CbFieldType.Object.value, data[0])
        assertEquals(7, data[1].toLong()) // Length

        assertEquals((CbFieldType.Array.value or CbFieldType.HasFieldName.value), data[2])
        assertEquals(1, data[3]) // Name length
        assertEquals('a'.toByte(), data[4]) // Name
        assertEquals(3, data[5]) // Length
        assertEquals(1, data[6]) // Item count

        assertEquals(CbFieldType.IntegerPositive.value, data[7])
        assertEquals(1, data[8])
    }

    @Test
    fun rawData() {
        val test = byteArrayOf(1, 2, 3, 4)

        val writer = CbWriter()
        writer.beginObject()
        writer.writeBinaryReference("a", ByteBuffer.wrap(test))
        writer.endObject()

        val data = writer.toByteArray()
        assertEquals(10, data.size)
        assertEquals(CbFieldType.Object.value, data[0])
        assertEquals(8, data[1]) // Length

        assertEquals((CbFieldType.Binary.value or CbFieldType.HasFieldName.value), data[2])
        assertEquals(1, data[3]) // Name length
        assertEquals('a'.toByte(), data[4]) // Name
        assertEquals(4, data[5]) // Length

        assertEquals(1, data[6])
        assertEquals(2, data[7])
        assertEquals(3, data[8])
        assertEquals(4, data[9])
    }

    @Test
    fun uniformArrayOfArrays() {
        val writer = CbWriter()
        writer.beginUniformArray(CbFieldType.Array)
        writer.beginArray()
        writer.writeNullValue()
        writer.endArray()
        writer.endArray()

        val data = writer.toByteArray()
        assertEquals(7, data.size)
        assertEquals(CbFieldType.UniformArray.value, data[0])
        assertEquals(5, data[1]) // Length
        assertEquals(1, data[2]) // Item Count

        assertEquals(CbFieldType.Array.value, data[3])
        assertEquals(2, data[4]) // Length
        assertEquals(1, data[5]) // Item Count

        assertEquals(CbFieldType.Null.value, data[6])
    }
}
