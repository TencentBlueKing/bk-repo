package com.tencent.bkrepo.common.artifact.stream

import com.tencent.bkrepo.common.api.util.randomString
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

internal class BoundedInputStreamTest {

    @Test
    fun testContentEquals() {
        val size = 10
        val content = randomString(size)
        val source = content.byteInputStream()
        val wrapper = BoundedInputStream(source, 100)
        val readContent = wrapper.readBytes().toString(Charset.defaultCharset())
        Assertions.assertEquals(content, readContent)
    }

    @Test
    fun testLimit() {
        val size = 10
        val content = randomString(size)
        val source = content.byteInputStream()
        val wrapper = BoundedInputStream(source, 5)
        Assertions.assertEquals(wrapper.available(), 5)
        Assertions.assertEquals(content[0], wrapper.read().toChar())
        Assertions.assertEquals(wrapper.available(), 4)

        Assertions.assertEquals(4, wrapper.read(ByteArray(5)))
        Assertions.assertEquals(wrapper.available(), 0)
        Assertions.assertEquals(-1, wrapper.read())
        Assertions.assertEquals(-1, wrapper.read(ByteArray(1)))
    }

    @Test
    fun testSkipAndLimit() {
        val size = 15
        val content = randomString(size)
        val source = content.byteInputStream()
        source.skip(5)
        val wrapper = BoundedInputStream(source, 3)
        Assertions.assertEquals(wrapper.available(), 3)
        Assertions.assertEquals(content[5], wrapper.read().toChar())
        Assertions.assertEquals(wrapper.available(), 2)

        Assertions.assertEquals(2, wrapper.read(ByteArray(5)))
        Assertions.assertEquals(wrapper.available(), 0)
        Assertions.assertEquals(-1, wrapper.read())
        Assertions.assertEquals(-1, wrapper.read(ByteArray(1)))
    }

}