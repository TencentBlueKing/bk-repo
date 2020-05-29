package com.tencent.bkrepo.common.artifact.resolve.file.stream

import com.tencent.bkrepo.common.api.util.randomString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

class OctetStreamArtifactFileTest {
    
    private val tempDir = System.getProperty("java.io.tmpdir")

    @Test
    fun testZeroThreshold() {
        val source = randomString(0).byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, 0, tempDir, true)
        assertTrue(artifactFile.isInMemory())
    }

    @Test
    fun testInMemory() {
        val randomString = randomString(10)
        val source = randomString.byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, 11, tempDir, true)
        assertTrue(artifactFile.isInMemory())
        val artifactFileString = artifactFile.getInputStream().readBytes().toString(Charset.defaultCharset())
        assertEquals(randomString, artifactFileString)
    }

    @Test
    fun testInFile() {
        val randomString = randomString(10)
        val source = randomString.byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, 9, tempDir, true)
        assertFalse(artifactFile.isInMemory())
        assertTrue(artifactFile.getFile()!!.exists())
        val artifactFileString = artifactFile.getInputStream().readBytes().toString(Charset.defaultCharset())
        assertEquals(randomString, artifactFileString)
    }

    @Test
    fun testBigSizeInMemory() {
        val randomString = randomString(1024 * 1024)
        val source = randomString.byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, 1024 * 1024 + 1, tempDir, true)
        assertTrue(artifactFile.isInMemory())
        val artifactFileString = artifactFile.getInputStream().readBytes().toString(Charset.defaultCharset())
        assertEquals(randomString, artifactFileString)
    }

    @Test
    fun testBigSizeInFile() {
        val randomString = randomString(1024 * 1024)
        val source = randomString.byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, 1024 * 1024 - 1, tempDir, true)
        assertFalse(artifactFile.isInMemory())
        assertTrue(artifactFile.getFile()!!.exists())
        val artifactFileString = artifactFile.getInputStream().readBytes().toString(Charset.defaultCharset())
        assertEquals(randomString, artifactFileString)
    }

    @Test
    fun testDeleteInMemory() {
        val randomString = randomString(10)
        val source = randomString.byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, 10, tempDir, true)
        assertTrue(artifactFile.isInMemory())
        assertNull(artifactFile.getFile())
        artifactFile.delete()
    }

    @Test
    fun testDeleteInFile() {
        val randomString = randomString(11)
        val source = randomString.byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, 10, tempDir, true)
        assertFalse(artifactFile.isInMemory())
        assertTrue(artifactFile.getFile()!!.exists())
        artifactFile.delete()
        assertFalse(artifactFile.getFile()!!.exists())
    }

    @Test
    fun testFlushToFile() {
        val randomString = randomString(10)
        val source = randomString.byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, 10, tempDir, true)
        assertTrue(artifactFile.isInMemory())
        assertNull(artifactFile.getFile())

        val file = artifactFile.flushToFile()
        assertTrue(file.exists())
        assertEquals(10, file.length())
        file.delete()
        assertFalse(file.exists())
    }
}
