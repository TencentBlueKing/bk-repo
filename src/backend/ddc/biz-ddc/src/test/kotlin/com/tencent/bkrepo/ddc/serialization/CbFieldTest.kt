package com.tencent.bkrepo.ddc.serialization

import com.tencent.bkrepo.ddc.serialization.CbField.CbFieldError.None
import com.tencent.bkrepo.ddc.serialization.CbField.CbFieldError.RangeError
import com.tencent.bkrepo.ddc.serialization.CbField.CbFieldError.TypeError
import com.tencent.bkrepo.ddc.utils.BlakeUtils
import com.tencent.bkrepo.ddc.utils.ByteBufferUtils
import com.tencent.bkrepo.ddc.utils.ByteBufferUtils.byteBufferOf
import com.tencent.bkrepo.ddc.utils.hasName
import com.tencent.bkrepo.ddc.utils.isArray
import com.tencent.bkrepo.ddc.utils.isBinary
import com.tencent.bkrepo.ddc.utils.isBinaryAttachment
import com.tencent.bkrepo.ddc.utils.isBool
import com.tencent.bkrepo.ddc.utils.isFloat
import com.tencent.bkrepo.ddc.utils.isHash
import com.tencent.bkrepo.ddc.utils.isInteger
import com.tencent.bkrepo.ddc.utils.isNull
import com.tencent.bkrepo.ddc.utils.isObject
import com.tencent.bkrepo.ddc.utils.isObjectAttachment
import com.tencent.bkrepo.ddc.utils.isString
import com.tencent.bkrepo.ddc.utils.isUuid
import com.tencent.bkrepo.ddc.utils.writeBinaryAttachment
import com.tencent.bkrepo.ddc.utils.writeBinaryAttachmentValue
import com.tencent.bkrepo.ddc.utils.writeObjectAttachment
import com.tencent.bkrepo.ddc.utils.writeString
import com.tencent.bkrepo.ddc.utils.writeStringValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.experimental.and
import kotlin.experimental.or

class CbFieldTest {
    @Test
    fun noneTest() {
        // Test CbField()
        val defaultField = CbField()
        assertFalse(CbFieldUtils.hasFieldName(defaultField.typeWithFlags))
        assertFalse(defaultField.hasValue())
        assertFalse(defaultField.hasError())
        assertTrue(defaultField.error == None)
        assertEquals(defaultField.getSize(), 1)
        assertEquals(defaultField.name.length, 0)
        assertFalse(defaultField.hasName())
        assertFalse(defaultField.hasValue())
        assertFalse(defaultField.hasError())
        assertEquals(defaultField.error, None)
        assertEquals(
            defaultField.getHash(),
            Blake3Hash.compute(ByteBuffer.wrap(ByteArray(1) { CbFieldType.None.value }))
        )
        assertFalse(defaultField.tryGetView().first)

        // Test CbField(None)
        var noneField = CbField(ByteBufferUtils.EMPTY, CbFieldType.None)
        assertEquals(noneField.getSize(), 1)
        assertEquals(noneField.name.length, 0)
        assertFalse(noneField.hasName())
        assertFalse(noneField.hasValue())
        assertFalse(noneField.hasError())
        assertEquals(noneField.error, None)
        assertEquals(noneField.getHash(), CbField().getHash())
        assertFalse(noneField.tryGetView().first)

        // Test CbField(None|Type|Name)
        var fieldType = CbFieldType.None.value or CbFieldType.HasFieldName.value
        var noneBytes = byteBufferOf(fieldType, 4, 'N'.toByte(), 'a'.toByte(), 'm'.toByte(), 'e'.toByte())

        noneField = CbField(noneBytes)
        assertEquals(noneField.getSize(), noneBytes.remaining())
        assertEquals(noneField.name, "Name")
        assertTrue(noneField.hasName())
        assertFalse(noneField.hasValue())
        assertEquals(noneField.getHash(), Blake3Hash.compute(noneBytes))
        var view = noneField.tryGetView()
        assertTrue(view.first && view.second == noneBytes)

        var copyBytes = ByteBuffer.allocate(noneBytes.remaining())
        noneField.copyTo(copyBytes)
        copyBytes.flip()
        assertTrue(noneBytes == copyBytes)


        // Test CbField(None|Type)
        fieldType = CbFieldType.None.value
        noneBytes = byteBufferOf(fieldType)
        noneField = CbField(noneBytes)
        assertEquals(noneField.getSize(), noneBytes.remaining())
        assertEquals(noneField.name.length, 0)
        assertFalse(noneField.hasName())
        assertFalse(noneField.hasValue())
        assertEquals(noneField.getHash(), CbField().getHash())
        view = noneField.tryGetView()
        assertTrue(view.first && view.second == noneBytes)


        // Test CbField(None|Name)
        fieldType = CbFieldType.None.value or CbFieldType.HasFieldName.value
        noneBytes = byteBufferOf(fieldType, 4, 'N'.toByte(), 'a'.toByte(), 'm'.toByte(), 'e'.toByte())

        var b = noneBytes.asReadOnlyBuffer()
        b.position(1)
        noneField = CbField(b.slice(), fieldType)
        assertEquals(noneField.getSize(), noneBytes.remaining())
        assertEquals(noneField.name, "Name")
        assertTrue(noneField.hasName())
        assertFalse(noneField.hasValue())
        assertEquals(noneField.getHash(), Blake3Hash.compute(noneBytes))

        view = noneField.tryGetView()
        assertFalse(view.first)

        copyBytes = ByteBuffer.allocate(noneBytes.remaining())
        noneField.copyTo(copyBytes)
        copyBytes.flip()
        assertTrue(noneBytes == copyBytes)

        // Test CbField(None|EmptyName)
        fieldType = CbFieldType.None.value or CbFieldType.HasFieldName.value
        noneBytes = byteBufferOf(fieldType, 0)

        b = noneBytes.asReadOnlyBuffer()
        b.position(1)
        noneField = CbField(b.slice(), fieldType)
        assertEquals(noneField.getSize(), noneBytes.remaining())
        assertEquals(noneField.name, "")
        assertTrue(noneField.hasName())
        assertFalse(noneField.hasValue())
        assertEquals(noneField.getHash(), Blake3Hash.compute(noneBytes))

        view = noneField.tryGetView()
        assertFalse(view.first)
    }

    @Test
    fun nullTest() {
        // Test CbField(Null)
        val nullField = CbField(ByteBufferUtils.EMPTY, CbFieldType.Null)
        assertEquals(nullField.getSize(), 1)
        assertTrue(nullField.isNull())
        assertTrue(nullField.hasValue())
        assertFalse(nullField.hasError())
        assertEquals(nullField.error, None)
        assertEquals(nullField.getHash(), Blake3Hash.compute(byteBufferOf(CbFieldType.Null.value)))

        // Test CbField(None) as Null
        val field = CbField()
        assertFalse(field.isNull())
    }

    @Test
    fun objectTest() {
        // Test CbField(Object, Empty)
        testField(CbFieldType.Object, ByteBuffer.wrap(ByteArray(1)))

        // Test CbField(Object, Empty)
        var obj = CbObject.EMPTY
        testIntObject(obj, 0, 1)

        // Find fields that do not exist.
        assertFalse(obj.find("Field").hasValue())
        assertFalse(obj.findIgnoreCase("Field").hasValue())
        assertFalse(obj["Field"].hasValue())

        // Advance an iterator past the last field.
        val iterator = obj.createIterator()
        assertFalse(iterator.hasNext())
        assertTrue(!iterator.hasNext())


        // Test CbField(Object, NotEmpty)
        val intType: Byte = (CbFieldType.HasFieldName.value or CbFieldType.IntegerPositive.value)
        var payload = byteBufferOf(
            12,
            intType, 1, 'A'.toByte(), 1,
            intType, 1, 'B'.toByte(), 2,
            intType, 1, 'C'.toByte(), 3
        )
        var field = CbField(payload.asReadOnlyBuffer(), CbFieldType.Object)
        testField(CbFieldType.Object, field, CbObject(payload, CbFieldType.Object))
        obj = field.asObject()
        testIntObject(obj, 3, payload.remaining())
        testIntObject(field.asObject(), 3, payload.remaining())
        assertTrue(obj.equals(field.asObject()))
        assertEquals(obj.find("B").asInt32(), 2)
        assertEquals(obj.find("b").asInt32(4), 4)
        assertEquals(obj.findIgnoreCase("B").asInt32(), 2)
        assertEquals(obj.findIgnoreCase("b").asInt32(), 2)
        assertEquals(obj["B"].asInt32(), 2)
        assertEquals(obj["b"].asInt32(4), 4)


        // Test CbField(UniformObject, NotEmpty)
        payload = byteBufferOf(
            10, intType,
            1, 'A'.toByte(), 1,
            1, 'B'.toByte(), 2,
            1, 'C'.toByte(), 3
        )
        field = CbField(payload.asReadOnlyBuffer(), CbFieldType.UniformObject)
        testField(CbFieldType.UniformObject, field, CbObject(payload, CbFieldType.UniformObject))
        obj = field.asObject()
        testIntObject(obj, 3, payload.remaining())
        testIntObject(field.asObject(), 3, payload.remaining())
        assertTrue(obj == field.asObject())
        assertEquals(obj.find("B").asInt32(), 2)
        assertEquals(obj.find("b").asInt32(4), 4)
        assertEquals(obj.findIgnoreCase("B").asInt32(), 2)
        assertEquals(obj.findIgnoreCase("b").asInt32(), 2)
        assertEquals(obj["B"].asInt32(), 2)
        assertEquals(obj["b"].asInt32(4), 4)

        // Equals
        val namedPayload = byteBufferOf(
            1, 'O'.toByte(),
            10, intType,
            1, 'A'.toByte(), 1,
            1, 'B'.toByte(), 2,
            1, 'C'.toByte(), 3
        )
        val namedField = CbField(namedPayload, CbFieldType.UniformObject.value or CbFieldType.HasFieldName.value)
        assertTrue(field.asObject() == namedField.asObject())

        // CopyTo
        val copyBytes = ByteBuffer.allocate(payload.remaining() + 1)
        field.asObject().copyTo(copyBytes)
        copyBytes.position(1)
        assertTrue(payload == copyBytes)

        copyBytes.clear()
        namedField.asObject().copyTo(copyBytes)
        copyBytes.position(1)
        assertTrue(payload == copyBytes)

        // Test CbField(None) as Object
        field = CbField.EMPTY
        testField(fieldType = CbFieldType.Object, field = field, expectedError = TypeError)

        // Test FCbObjectView(ObjectWithName) and CreateIterator
        val objectType = (CbFieldType.Object.value or CbFieldType.HasFieldName.value)
        val buffer = byteBufferOf(
            objectType,
            3, 'K'.toByte(), 'e'.toByte(), 'y'.toByte(),
            4,
            (CbFieldType.HasFieldName.value or CbFieldType.IntegerPositive.value),
            1, 'F'.toByte(),
            8
        )
        var o = CbObject(buffer)
        assertEquals(o.getSize(), 6)
        for (f in o) {
            assertEquals(f.name, "F")
            assertEquals(f.asInt32(), 8)
        }

        // Test FCbObjectView as CbFieldIterator
        var count = 0
        o = CbObject.EMPTY
        for (f in CbFieldIterator.makeSingle(o.asField())) {
            assertTrue(f.isObject())
            ++count
        }
        assertEquals(count, 1)
    }

    @Test
    fun arrayTest() {
        // Test CbField(Array, Empty)
        testField(CbFieldType.Array, byteBufferOf(1, 0))

        // Test CbField(Array, Empty)
        var array = CbArray()
        testIntArray(array, 0, 2)

        // Advance an iterator past the last field.
        val iterator = array.createIterator()
        assertFalse(iterator.hasNext())
        for (count in 16 downTo 1) {
            iterator.next().asInt32()
        }
        assertFalse(iterator.hasNext())

        // Test CbField(Array, NotEmpty)
        val intType: Byte = CbFieldType.IntegerPositive.value
        var payload = byteBufferOf(7, 3, intType, 1, intType, 2, intType, 3)
        var field = CbField(payload, CbFieldType.Array)
        testField(CbFieldType.Array, field, CbArray(payload, CbFieldType.Array))
        array = field.asArray()
        testIntArray(array, 3, payload.remaining())
        testIntArray(field.asArray(), 3, payload.remaining())
        assertTrue(array == field.asArray())

        // Test CbField(UniformArray)
        payload = byteBufferOf(5, 3, intType, 1, 2, 3)
        field = CbField(payload, CbFieldType.UniformArray)
        testField(CbFieldType.UniformArray, field, CbArray(payload, CbFieldType.UniformArray))
        array = field.asArray()
        testIntArray(array, 3, payload.remaining())
        testIntArray(field.asArray(), 3, payload.remaining())
        assertTrue(array == field.asArray())

        // Equals
        val namedPayload = byteBufferOf(1, 'A'.toByte(), 5, 3, intType, 1, 2, 3)
        val namedField = CbField(namedPayload, CbFieldType.UniformArray.value or CbFieldType.HasFieldName.value)
        assertTrue(field.asArray() == namedField.asArray())
        assertTrue(field == field.asArray().asField())
        assertTrue(namedField == namedField.asArray().asField())

        // CopyTo
        val copyBytes = ByteBuffer.allocate(payload.remaining() + CbFieldType.SIZE_OF_CB_FIELD_TYPE)
        field.asArray().copyTo(copyBytes)
        copyBytes.position(1)
        assertTrue(payload == copyBytes)

        copyBytes.clear()
        namedField.asArray().copyTo(copyBytes)
        copyBytes.position(1)
        assertTrue(payload == copyBytes)

        // TryGetView
        assertFalse(field.asArray().tryGetView().first)
        assertFalse(namedField.asArray().tryGetView().first)

        // Test CbField(None) as Array
        field = CbField()
        testField(fieldType = CbFieldType.Array, field = field, expectedError = TypeError)


        // Test CbArray(ArrayWithName) and CreateIterator
        val arrayType: Byte = (CbFieldType.Array.value or CbFieldType.HasFieldName.value)
        val buffer = byteBufferOf(
            arrayType,
            3, 'K'.toByte(), 'e'.toByte(), 'y'.toByte(),
            3, 1, CbFieldType.IntegerPositive.value, 8
        )
        array = CbArray(buffer)
        assertEquals(array.getSize(), 5)
        for (f in array) {
            assertEquals(f.asInt32(), 8)
        }


        // Test CbArray as CbFieldIterator
        var count = 0
        array = CbArray()
        for (f in CbFieldIterator.makeSingle(array.asField())) {
            assertTrue(f.isArray())
            ++count
        }
        assertEquals(count, 1)
    }

    @Test
    fun binaryTest() {
        // Test CbField(Binary, Empty)
        testField(CbFieldType.Binary, byteBufferOf(0))

        // Test CbField(Binary, Value)
        run {
            val payload = byteBufferOf(3, 4, 5, 6) // Size: 3, Data: 4/5/6
            val fieldView = CbField(payload.asReadOnlyBuffer(), CbFieldType.Binary)
            payload.position(1)
            testField(CbFieldType.Binary, fieldView, payload.slice())
        }

        // Test CbField(None) as Binary
        run {
            val fieldView = CbField()
            val default = byteBufferOf(1, 2, 3)
            testField(
                fieldType = CbFieldType.Binary,
                field = fieldView,
                expectedError = TypeError,
                expectedValue = default,
                defaultValue = default,
            )
        }
    }

    @Test
    fun stringTest() {
        // Test CbField(String, Empty)
        testField(CbFieldType.String, byteBufferOf(0))

        // Test CbField(String, Value)
        run {
            val payload = byteBufferOf(3, 'A'.toByte(), 'B'.toByte(), 'C'.toByte()) // Size: 3, Data: ABC
            testField(CbFieldType.String, payload, "ABC")
        }

        // Test CbField (String, OutOfRangeSize)
        run {
            val payload = ByteBuffer.allocate(9)
            VarULong.writeUnsigned(payload, (1L shl 31))
            payload.flip()
            testField(
                fieldType = CbFieldType.String,
                payload = payload,
                expectedError = RangeError,
                defaultValue = "ABC",
                expectedValue = "ABC",
            )
        }

        // Test CbField(None) as String
        run {
            val field = CbField()
            testField(
                fieldType = CbFieldType.String,
                field = field,
                expectedError = TypeError,
                defaultValue = "ABC",
                expectedValue = "ABC",
            )
        }
    }

    @Test
    fun integerTest() {
        // Test CbField(IntegerPositive)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos7, 0x00)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos7, 0x7f)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos8, 0x80)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos8, 0xff)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos15, 0x0100)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos15, 0x7fff)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos16, 0x8000)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos16, 0xffff)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos31, 0x0001_0000)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos31, 0x7fff_ffff)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos32, 0x8000_0000)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos32, 0xffff_ffff)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos63, 0x0000_0001_0000_0000)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos63, 0x7fff_ffff_ffff_ffff)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos64, Long.MIN_VALUE)
        testIntegerField(CbFieldType.IntegerPositive, EIntType.Pos64, -1L)

        // Test CbField(IntegerNegative)
        testIntegerField(CbFieldType.IntegerNegative, EIntType.Neg7, 0x01)
        testIntegerField(CbFieldType.IntegerNegative, EIntType.Neg7, 0x80)
        testIntegerField(CbFieldType.IntegerNegative, EIntType.Neg15, 0x81)
        testIntegerField(CbFieldType.IntegerNegative, EIntType.Neg15, 0x8000)
        testIntegerField(CbFieldType.IntegerNegative, EIntType.Neg31, 0x8001)
        testIntegerField(CbFieldType.IntegerNegative, EIntType.Neg31, 0x8000_0000)
        testIntegerField(CbFieldType.IntegerNegative, EIntType.Neg63, 0x8000_0001)
        testIntegerField(CbFieldType.IntegerNegative, EIntType.Neg63, Long.MIN_VALUE)
        testIntegerField(CbFieldType.IntegerNegative, EIntType.None, Long.MIN_VALUE + 1)
        testIntegerField(CbFieldType.IntegerNegative, EIntType.None, -1L)

        // Test CbField(None) as Integer
        val field = CbField()
        testField(
            fieldType = CbFieldType.IntegerPositive,
            field = field,
            expectedError = TypeError,
            expectedValue = 8L,
            defaultValue = 8L
        )
        testField(
            fieldType = CbFieldType.IntegerNegative,
            field = field,
            expectedError = TypeError,
            expectedValue = 8L,
            defaultValue = 8L
        )
    }

    @Test
    fun floatTest() {
        // Test CbField(Float, 32-bit)
        run {
            val payload = byteBufferOf(0xc0.toByte(), 0x12, 0x34, 0x56) // -2.28444433f
            // TODO 存在精度问题
            testField(CbFieldType.Float32, payload, -2.2844443f)
            val field = CbField(payload, CbFieldType.Float32)
            testField(CbFieldType.Float64, field, -2.2844443321228027)
        }

        // Test CbField(Float, 64-bit)
        run {
            val payload = byteBufferOf(
                0xc1.toByte(),
                0x23,
                0x45,
                0x67,
                0x89.toByte(),
                0xab.toByte(),
                0xcd.toByte(),
                0xef.toByte()
            ) // -631475.76888888876
            testField(CbFieldType.Float64, payload, -631475.76888888876)
            val field = CbField(payload, CbFieldType.Float64)
            testField(
                fieldType = CbFieldType.Float32,
                field = field,
                expectedError = RangeError,
                expectedValue = 8.0f,
                defaultValue = 8.0f,
            )
        }

        // Test CbField(Integer+, MaxBinary32) as Float
        run {
            val payload = ByteBuffer.allocate(9)
            VarULong.writeUnsigned(payload, (1L shl 24) - 1) // 16,777,215
            val field = CbField(payload, CbFieldType.IntegerPositive)
            testField(CbFieldType.Float32, field, 16_777_215.0f)
            testField(CbFieldType.Float64, field, 16_777_215.0)
        }

        // Test CbField(Integer+, MaxBinary32+1) as Float
        run {
            val payload = ByteBuffer.allocate(9)
            VarULong.writeUnsigned(payload, 1L shl 24) // 16,777,216
            val field = CbField(payload, CbFieldType.IntegerPositive)
            testField(
                fieldType = CbFieldType.Float32,
                field = field,
                expectedError = RangeError,
                expectedValue = 8.0f,
                defaultValue = 8.0f
            )
            testField(CbFieldType.Float64, field, 16_777_216.0)
        }

        // Test CbField(Integer+, MaxBinary64) as Float
        run {
            val payload = ByteBuffer.allocate(9)
            VarULong.writeUnsigned(payload, (1L shl 53) - 1) // 9,007,199,254,740,991
            val field = CbField(payload, CbFieldType.IntegerPositive)
            testField(
                fieldType = CbFieldType.Float32,
                field = field,
                expectedError = RangeError,
                expectedValue = 8.0f,
                defaultValue = 8.0f
            )
            testField(CbFieldType.Float64, field, 9_007_199_254_740_991.0)
        }

        // Test CbField(Integer+, MaxBinary64+1) as Float
        run {
            val payload = ByteBuffer.allocate(9)
            VarULong.writeUnsigned(payload, 1L shl 53) // 9,007,199,254,740,992
            val field = CbField(payload, CbFieldType.IntegerPositive)
            testField(
                fieldType = CbFieldType.Float32,
                field = field,
                expectedError = RangeError,
                expectedValue = 8.0f,
                defaultValue = 8.0f
            )
            testField(
                fieldType = CbFieldType.Float64,
                field = field,
                expectedError = RangeError,
                expectedValue = 8.0,
                defaultValue = 8.0
            )
        }

        // Test CbField(Integer+, MaxUInt64) as Float
        run {
            val payload = ByteBuffer.allocate(9)
            VarULong.writeUnsigned(payload, (0L.inv())) // Max uint64
            val field = CbField(payload, CbFieldType.IntegerPositive)
            testField(
                fieldType = CbFieldType.Float32,
                field = field,
                expectedError = RangeError,
                expectedValue = 8.0f,
                defaultValue = 8.0f
            )
            testField(
                fieldType = CbFieldType.Float64,
                field = field,
                expectedError = RangeError,
                expectedValue = 8.0,
                defaultValue = 8.0
            )
        }

        // Test CbField(Integer-, MaxBinary32) as Float
        run {
            val payload = ByteBuffer.allocate(9)
            VarULong.writeUnsigned(payload, (1L shl 24) - 2) // -16,777,215
            val field = CbField(payload, CbFieldType.IntegerNegative)
            testField(CbFieldType.Float32, field, -16_777_215.0f)
            testField(CbFieldType.Float64, field, -16_777_215.0)
        }

        // Test CbField(Integer-, MaxBinary32+1) as Float
        run {
            val payload = ByteBuffer.allocate(9)
            VarULong.writeUnsigned(payload, (1L shl 24) - 1) // -16,777,216
            val field = CbField(payload, CbFieldType.IntegerNegative)
            testField(
                fieldType = CbFieldType.Float32,
                field = field,
                expectedError = RangeError,
                expectedValue = 8.0f,
                defaultValue = 8.0f
            )
            testField(CbFieldType.Float64, field, -16_777_216.0)
        }

// Test CbField(Integer-, MaxBinary64) as Float
        run {
            val payload = ByteBuffer.allocate(9)
            VarULong.writeUnsigned(payload, (1L shl 53) - 2) // -9,007,199,254,740,991
            val field = CbField(payload, CbFieldType.IntegerNegative)
            testField(
                fieldType = CbFieldType.Float32,
                field = field,
                expectedError = RangeError,
                expectedValue = 8.0f,
                defaultValue = 8.0f
            )
            testField(CbFieldType.Float64, field, -9_007_199_254_740_991.0)
        }

// Test CbField(Integer-, MaxBinary64+1) as Float
        run {
            val payload = ByteBuffer.allocate(9)
            VarULong.writeUnsigned(payload, (1L shl 53) - 1) // -9,007,199,254,740,992
            val field = CbField(payload, CbFieldType.IntegerNegative)
            testField(
                fieldType = CbFieldType.Float32,
                field = field,
                expectedError = RangeError,
                expectedValue = 8.0f,
                defaultValue = 8.0f
            )
            testField(
                fieldType = CbFieldType.Float64,
                field = field,
                expectedError = RangeError,
                expectedValue = 8.0,
                defaultValue = 8.0
            )
        }

// Test CbField(None) as Float
        run {
            val field = CbField()
            testField(
                fieldType = CbFieldType.Float32,
                field = field,
                expectedError = TypeError,
                expectedValue = 8.0f,
                defaultValue = 8.0f
            )
            testField(
                fieldType = CbFieldType.Float64,
                field = field,
                expectedError = TypeError,
                expectedValue = 8.0,
                defaultValue = 8.0
            )
        }
    }

    @Test
    fun boolTest() {
        // Test CbField(Bool, False)
        testField(CbFieldType.BoolFalse, ByteBufferUtils.EMPTY, expectedValue = false, defaultValue = true)

        // Test CbField(Bool, True)
        testField(CbFieldType.BoolTrue, ByteBufferUtils.EMPTY, expectedValue = true, defaultValue = false)

        // Test CbField(None) as Bool
        val defaultField = CbField()
        testField(
            fieldType = CbFieldType.BoolFalse,
            field = defaultField,
            expectedError = TypeError,
            expectedValue = false,
            defaultValue = false
        )
        testField(
            fieldType = CbFieldType.BoolTrue,
            field = defaultField,
            expectedError = TypeError,
            expectedValue = true,
            defaultValue = true
        )
    }

    @Test
    fun objectAttachmentTest() {
        val zeroBytes = ByteBuffer.allocate(20)
        val sequentialBytes = byteBufferOf(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20
        )

        // Test CbField(ObjectAttachment, Zero)
        testField(fieldType = CbFieldType.ObjectAttachment, payload = zeroBytes)

        // Test CbField(ObjectAttachment, NonZero)
        testField(
            CbFieldType.ObjectAttachment,
            sequentialBytes,
            CbObjectAttachment(IoHash(sequentialBytes.asReadOnlyBuffer()))
        )

        // Test CbField(ObjectAttachment, NonZero) AsAttachment
        sequentialBytes.clear()
        val field = CbField(sequentialBytes, CbFieldType.ObjectAttachment)
        testField(
            CbFieldType.ObjectAttachment,
            field,
            CbObjectAttachment(IoHash(sequentialBytes.asReadOnlyBuffer())),
            CbObjectAttachment(IoHash.ZERO),
            None
        )

        // Test CbField(None) as ObjectAttachment
        val defaultField = CbField()
        testField(
            fieldType = CbFieldType.ObjectAttachment,
            field = defaultField,
            expectedError = TypeError,
            defaultValue = CbObjectAttachment(IoHash(sequentialBytes.asReadOnlyBuffer())),
            expectedValue = CbObjectAttachment(IoHash(sequentialBytes.asReadOnlyBuffer()))
        )
    }

    @Test
    fun binaryAttachmentTest() {
        val zeroBytes = ByteBuffer.allocate(20)
        val sequentialBytes = byteBufferOf(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20
        )

        // Test CbField(BinaryAttachment, Zero)
        testField(CbFieldType.BinaryAttachment, zeroBytes)

        // Test CbField(BinaryAttachment, NonZero)
        testField(
            CbFieldType.BinaryAttachment,
            sequentialBytes,
            CbBinaryAttachment(IoHash(sequentialBytes.asReadOnlyBuffer()))
        )

        // Test CbField(BinaryAttachment, NonZero) AsAttachment
        val field = CbField(sequentialBytes, CbFieldType.BinaryAttachment)
        testField(
            CbFieldType.BinaryAttachment,
            field,
            CbBinaryAttachment(IoHash(sequentialBytes.asReadOnlyBuffer())),
            CbBinaryAttachment(IoHash.ZERO),
            None
        )

        // Test CbField(None) as BinaryAttachment
        val defaultField = CbField()
        testField(
            fieldType = CbFieldType.BinaryAttachment,
            field = defaultField,
            expectedError = TypeError,
            expectedValue = CbBinaryAttachment(IoHash(sequentialBytes.asReadOnlyBuffer())),
            defaultValue = CbBinaryAttachment(IoHash(sequentialBytes.asReadOnlyBuffer())),
        )
    }

    @Test
    fun iterateAttachmentTest() {
        val hash = BlakeUtils.hash("hello world")

        val writer = CbWriter().apply {
            beginObject()
            writeString("TestString", "test")
            writeBinaryAttachment("TestBinary", hash)
            writeObjectAttachment("TestObject", hash)

            // inner object
            beginObject("InnerObject")
            writeString("TestString", "test")
            writeBinaryAttachment("TestBinary", hash)
            writeObjectAttachment("TestObject", hash)
            endObject()

            // inner uniform array
            beginArray("InnerArray", CbFieldType.BinaryAttachment)
            writeBinaryAttachmentValue(hash)
            writeBinaryAttachmentValue(hash)
            endArray()

            // inner array
            beginArray("InnerArray2")
            writeStringValue("test")
            writeBinaryAttachmentValue(hash)
            endArray()

            endObject()
        }

        val field = CbField(ByteBuffer.wrap(writer.toByteArray()))

        var count = 0
        field.asObject().iterateAttachments { count++ }
        assertEquals(7, count)
    }

    @Test
    fun hashTest() {
        val zeroBytes = ByteBuffer.allocate(20)
        val sequentialBytes = byteBufferOf(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20
        )

        // Test CbField(Hash, Zero)
        testField(CbFieldType.Hash, zeroBytes)

        // Test CbField(Hash, NonZero)
        testField(CbFieldType.Hash, sequentialBytes, IoHash(sequentialBytes.asReadOnlyBuffer()))

        // Test CbField(None) as Hash
        val defaultField = CbField()
        testField(
            fieldType = CbFieldType.Hash,
            field = defaultField,
            expectedError = TypeError,
            expectedValue = IoHash(sequentialBytes.asReadOnlyBuffer()),
            defaultValue = IoHash(sequentialBytes.asReadOnlyBuffer()),
        )

        // Test CbField(ObjectAttachment) as Hash
        val field = CbField(sequentialBytes.asReadOnlyBuffer(), CbFieldType.ObjectAttachment)
        testField(CbFieldType.Hash, field, IoHash(sequentialBytes.asReadOnlyBuffer()))

        // Test CbField(BinaryAttachment) as Hash
        val binaryAttachmentField = CbField(sequentialBytes.asReadOnlyBuffer(), CbFieldType.BinaryAttachment)
        testField(CbFieldType.Hash, binaryAttachmentField, IoHash(sequentialBytes.asReadOnlyBuffer()))
    }

    @Test
    fun uuidTest() {
        // TODO 与C# GUID不同，需要确认兼容性
        val zeroBytes = byteBufferOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val sequentialBytes = byteBufferOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
        val sequentialGuid = UUID.fromString("03020100-0504-0706-0809-0a0b0c0d0e0f")

        // Test CbField(Uuid, Zero)
        testField(CbFieldType.Uuid, zeroBytes, UUID(0, 0), sequentialGuid)

        // Test CbField(Uuid, NonZero)
        testField(CbFieldType.Uuid, sequentialBytes, sequentialGuid, UUID(0, 0))

        // Test CbField(None) as Uuid
        val defaultField = CbField()
        val expectedValue = UUID.randomUUID()
        testField(
            fieldType = CbFieldType.Uuid,
            field = defaultField,
            expectedError = TypeError,
            expectedValue = expectedValue,
            defaultValue = expectedValue
        )
    }

    // ------------ test utils ------------------
    enum class EIntType(val value: Byte) {
        None(0x00),
        Int8(0x01),
        Int16(0x02),
        Int32(0x04),
        Int64(0x08),
        UInt8(0x10),
        UInt16(0x20),
        UInt32(0x40),
        UInt64(0x80.toByte()),

        // Masks for positive values requiring the specified number of bits.
        Pos64(UInt64.value),
        Pos63(Pos64.value or Int64.value),
        Pos32(Pos63.value or UInt32.value),
        Pos31(Pos32.value or Int32.value),
        Pos16(Pos31.value or UInt16.value),
        Pos15(Pos16.value or Int16.value),
        Pos8(Pos15.value or UInt8.value),
        Pos7(Pos8.value or Int8.value),

        // Masks for negative values requiring the specified number of bits.
        Neg63(Int64.value),
        Neg31(Neg63.value or Int32.value),
        Neg15(Neg31.value or Int16.value),
        Neg7(Neg15.value or Int8.value)
    }

    private fun testIntegerField(fieldType: CbFieldType, expectedMask: EIntType, magnitude: Long) {
        val payload = ByteBuffer.allocate(9)
        val negative = (fieldType.value and 1.toByte())
        VarULong.writeUnsigned(payload, magnitude - negative)
        val defaultValue = 8L
        val expectedValue = if (negative != 0.toByte()) -magnitude else magnitude
        val field = CbField(payload, fieldType)

        var finalExpectedValue: Any? = if ((expectedMask.value and EIntType.Int8.value) != 0.toByte()) {
            expectedValue.toByte()
        } else {
            defaultValue.toByte()
        }
        testField(
            CbFieldType.IntegerNegative,
            field,
            finalExpectedValue,
            defaultValue.toByte(),
            if ((expectedMask.value and EIntType.Int8.value) != 0.toByte()) None else RangeError,
            CbFieldAccessors.fromStruct<Byte>(
                defaultValue = 0,
                isType = { x -> x.isInteger() },
                asTypeWithDefault = { x, y -> x.asInt8(y) })
        )

        finalExpectedValue = (if ((expectedMask.value and EIntType.Int16.value) != 0.toByte()) {
            expectedValue.toShort()
        } else {
            defaultValue.toShort()
        })
        testField(
            CbFieldType.IntegerNegative,
            field,
            finalExpectedValue,
            defaultValue.toShort(),
            if ((expectedMask.value and EIntType.Int16.value) != 0.toByte()) None else RangeError,
            CbFieldAccessors.fromStruct<Short>(
                defaultValue = 0,
                isType = { x -> x.isInteger() },
                asTypeWithDefault = { x, y -> x.asInt16(y) })
        )

        finalExpectedValue = if ((expectedMask.value and EIntType.Int32.value) != 0.toByte()) {
            expectedValue.toInt()
        } else {
            defaultValue.toInt()
        }
        testField(
            CbFieldType.IntegerNegative,
            field,
            finalExpectedValue,
            defaultValue.toInt(),
            if ((expectedMask.value and EIntType.Int32.value) != 0.toByte()) None else RangeError,
            CbFieldAccessors.fromStruct<Int>(0, { x -> x.isInteger() }, { x, y -> x.asInt32(y) })
        )

        testField(
            CbFieldType.IntegerNegative,
            field,
            ((if ((expectedMask.value and EIntType.Int64.value) != 0.toByte()) expectedValue else defaultValue)),
            defaultValue,
            if ((expectedMask.value and EIntType.Int64.value) != 0.toByte()) None else RangeError,
            CbFieldAccessors.fromStruct<Long>(
                0L,
                isType = { x -> x.isInteger() },
                asTypeWithDefault = { x, y -> x.asInt64(y) })
        )

        finalExpectedValue = if ((expectedMask.value and EIntType.UInt8.value) != 0.toByte()) {
            expectedValue.toByte()
        } else {
            defaultValue.toByte()
        }
        testField(
            CbFieldType.IntegerPositive,
            field,
            finalExpectedValue,
            defaultValue.toByte(),
            if ((expectedMask.value and EIntType.UInt8.value) != 0.toByte()) None else RangeError,
            CbFieldAccessors.fromStruct<Byte>(0, { x -> x.isInteger() }, { x, y -> x.asUInt8(y) })
        )

        finalExpectedValue = if ((expectedMask.value and EIntType.UInt16.value) != 0.toByte()) {
            expectedValue.toShort()
        } else {
            defaultValue.toShort()
        }
        testField(
            CbFieldType.IntegerPositive,
            field,
            finalExpectedValue,
            defaultValue.toShort(),
            if ((expectedMask.value and EIntType.UInt16.value) != 0.toByte()) None else RangeError,
            CbFieldAccessors.fromStruct<Short>(0, { x -> x.isInteger() }, { x, y -> x.asUInt16(y) })
        )

        finalExpectedValue = if ((expectedMask.value and EIntType.UInt32.value) != 0.toByte()) {
            expectedValue.toInt()
        } else {
            defaultValue.toInt()
        }
        testField(
            CbFieldType.IntegerPositive,
            field,
            finalExpectedValue,
            defaultValue.toInt(),
            if ((expectedMask.value and EIntType.UInt32.value) != 0.toByte()) None else RangeError,
            CbFieldAccessors.fromStruct<Int>(0, { x -> x.isInteger() }, { x, y -> x.asUInt32(y) })
        )

        testField(
            CbFieldType.IntegerPositive,
            field,
            ((if ((expectedMask.value and EIntType.UInt64.value) != 0.toByte()) expectedValue else defaultValue)),
            defaultValue,
            if ((expectedMask.value and EIntType.UInt64.value) != 0.toByte()) None else RangeError,
            CbFieldAccessors.fromStruct<Long>(0L, { x -> x.isInteger() }, { x, y -> x.asUInt64(y) })
        )
    }

    private fun testIntArray(array: CbArray, expectedNum: Int, expectedPayloadSize: Int) {
        assertEquals(array.getSize(), expectedPayloadSize + CbFieldType.SIZE_OF_CB_FIELD_TYPE)
        assertEquals(array.count, expectedNum)

        var actualNum = 0
        for (field in array) {
            ++actualNum
            assertEquals(field.asInt32(), actualNum)
        }
        assertEquals(actualNum, expectedNum)

        actualNum = 0
        for (field in array.asField()) {
            ++actualNum
            assertEquals(field.asInt32(), actualNum)
        }
        assertEquals(actualNum, expectedNum)
    }

    private fun testIntObject(obj: CbObject, expectedNum: Int, expectedPayloadSize: Int) {
        assertEquals(obj.getSize(), expectedPayloadSize + CbFieldType.SIZE_OF_CB_FIELD_TYPE)

        var actualNum = 0
        for (field in obj) {
            ++actualNum
            assertNotEquals(field.name.length, 0)
            assertEquals(field.asInt32(), actualNum)
        }
        assertEquals(expectedNum, actualNum)
    }

    private fun testField(
        fieldType: CbFieldType,
        payload: ByteBuffer,
        expectedValue: Any? = null,
        defaultValue: Any? = null,
        expectedError: CbField.CbFieldError = None,
        accessors: CbFieldAccessors? = null,
    ) {
        val field = CbField(payload, fieldType)
        assertEquals(
            payload.remaining() + if (CbFieldUtils.hasFieldType(fieldType.value)) 0 else 1,
            field.getSize()
        )
        assertTrue(field.hasValue())
        assertFalse(field.hasError())
        assertEquals(None, field.error)
        testField(fieldType, field, expectedValue, defaultValue, expectedError, accessors)
    }

    private fun testField(
        fieldType: CbFieldType,
        field: CbField,
        expectedValue: Any? = null,
        defaultValue: Any? = null,
        expectedError: CbField.CbFieldError = None,
        accessors: CbFieldAccessors? = null
    ) {
        val actualAccessors = accessors ?: typeAccessors[fieldType]!!
        val actualExpectedValue = expectedValue ?: actualAccessors.defaultValue
        val actualDefaultValue = defaultValue ?: actualAccessors.defaultValue

        assertEquals(actualAccessors.isType(field), expectedError != TypeError)
        if (expectedError == None && !field.isBool()) {
            assertFalse(field.asBool())
            assertTrue(field.hasError())
            assertEquals(field.error, TypeError)
        }

        assertTrue(actualAccessors.asTypeWithDefault(field, actualDefaultValue) == actualExpectedValue)
        assertEquals(field.hasError(), expectedError != None)
        assertEquals(field.error, expectedError)
    }

    private val typeAccessors = mapOf(
        CbFieldType.Object to CbFieldAccessors(
            CbObject.EMPTY,
            { it.isObject() }
        ) { x, y -> x.asObject() },
        CbFieldType.UniformObject to CbFieldAccessors(
            CbObject.EMPTY,
            { it.isObject() }
        ) { x, y -> x.asObject() },
        CbFieldType.Array to CbFieldAccessors(
            CbArray.EMPTY,
            { it.isArray() }
        ) { x, y -> x.asArray() },
        CbFieldType.UniformArray to CbFieldAccessors(
            CbArray.EMPTY,
            { it.isArray() }
        ) { x, y -> x.asArray() },
        CbFieldType.Binary to CbFieldAccessors.fromStruct<ByteBuffer>(
            ByteBufferUtils.EMPTY,
            { it.isBinary() },
            { x, y -> x.asBinary(y) }
        ),
        CbFieldType.String to CbFieldAccessors(
            "",
            { it.isString() }
        ) { x, default -> x.asString(default as String) },
        CbFieldType.IntegerPositive to CbFieldAccessors.fromStruct<Long>(
            0,
            { it.isInteger() },
            { x, y -> x.asUInt64(y) }
        ),
        CbFieldType.IntegerNegative to CbFieldAccessors.fromStruct<Long>(
            0,
            { it.isInteger() },
            { x, y -> x.asInt64(y) }
        ),
        CbFieldType.Float32 to CbFieldAccessors.fromStruct<Float>(
            0.0f,
            { it.isFloat() },
            { x, y -> x.asFloat(y) },
            { x, y -> x.compareTo(y) == 0 }
        ),
        CbFieldType.Float64 to CbFieldAccessors.fromStruct<Double>(
            0.0,
            { it.isFloat() },
            { x, y -> x.asDouble(y) },
            { x, y -> x.compareTo(y) == 0 }
        ),
        CbFieldType.BoolTrue to CbFieldAccessors.fromStruct<Boolean>(
            true,
            { it.isBool() },
            { x, y -> x.asBool(y) }
        ),
        CbFieldType.BoolFalse to CbFieldAccessors.fromStruct<Boolean>(
            false,
            { it.isBool() },
            { x, y -> x.asBool(y) }
        ),
        CbFieldType.ObjectAttachment to CbFieldAccessors(
            CbObjectAttachment(IoHash.ZERO),
            { it.isObjectAttachment() }
        ) { x, default -> x.asObjectAttachment(default as CbObjectAttachment) },
        CbFieldType.BinaryAttachment to CbFieldAccessors(
            CbBinaryAttachment(IoHash.ZERO),
            { it.isBinaryAttachment() }
        ) { x, default -> x.asBinaryAttachment(default as CbBinaryAttachment) },
        CbFieldType.Hash to CbFieldAccessors(
            IoHash.ZERO,
            { it.isHash() }
        ) { x, default -> x.asHash(default as IoHash) },
        CbFieldType.Uuid to CbFieldAccessors.fromStruct<UUID>(
            UUID.nameUUIDFromBytes(ByteArray(16)),
            { it.isUuid() },
            { x, y -> x.asUuid(y) }
        ),
//        CbFieldType.DateTime to CbFieldAccessors.fromStruct<DateTime>(
//            DateTime(0, DateTimeZone.UTC),
//            { it.isDateTime() },
//            { x, y -> x.asDateTime(y) }
//        ),
//        CbFieldType.TimeSpan to CbFieldAccessors.fromStruct<TimeSpan>(
//            { it.isTimeSpan() },
//            { x, y -> x.asTimeSpan(y) }
//        )
    )

    class CbFieldAccessors(
        var defaultValue: Any?,
        var isType: (CbField) -> Boolean,
        var asTypeWithDefault: (CbField, Any?) -> Any?,
    ) {
        var comparer: (Any?, Any?) -> Boolean = { x, y -> x?.equals(y) == true }

        companion object {
            inline fun <reified T : Any> fromStruct(
                defaultValue: Any? = T::class.java.newInstance(),
                noinline isType: (CbField) -> Boolean,
                crossinline asTypeWithDefault: (CbField, T) -> Any?,
                crossinline comparer: (T, T) -> Boolean = { x, y -> x == y },
            ): CbFieldAccessors {
                return CbFieldAccessors(
                    defaultValue,
                    isType,
                    { field, y -> asTypeWithDefault(field, y as T) }
                ).apply { this.comparer = { x, y -> comparer(x as T, y as T) } }
            }
        }
    }
}
