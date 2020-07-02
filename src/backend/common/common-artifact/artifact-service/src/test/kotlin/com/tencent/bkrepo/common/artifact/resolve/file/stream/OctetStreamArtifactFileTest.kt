package com.tencent.bkrepo.common.artifact.resolve.file.stream

import com.tencent.bkrepo.common.api.constant.StringPool.randomString
import com.tencent.bkrepo.common.storage.config.UploadProperties
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.monitor.MonitorProperties
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.util.unit.DataSize
import java.nio.charset.Charset

class OctetStreamArtifactFileTest {
    
    private val tempDir = System.getProperty("java.io.tmpdir")

    private val uploadProperties = UploadProperties(location = tempDir)

    private fun createMonitor(threshold: Long): StorageHealthMonitor {
        val storageProperties = StorageProperties(
            upload = uploadProperties,
            fileSizeThreshold = DataSize.ofBytes(threshold),
            monitor = MonitorProperties()
        )
        return StorageHealthMonitor(storageProperties)
    }

    @Test
    fun testZeroThreshold() {
        val source = randomString(0).byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, createMonitor(0))
        assertTrue(artifactFile.isInMemory())
    }

    @Test
    fun testInMemory() {
        val randomString = randomString(10)
        val source = randomString.byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, createMonitor(11))
        assertTrue(artifactFile.isInMemory())
        val artifactFileString = artifactFile.getInputStream().readBytes().toString(Charset.defaultCharset())
        assertEquals(randomString, artifactFileString)
    }

    @Test
    fun testInFile() {
        val randomString = randomString(10)
        val source = randomString.byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, createMonitor(9))
        assertFalse(artifactFile.isInMemory())
        assertTrue(artifactFile.getFile()!!.exists())
        val artifactFileString = artifactFile.getInputStream().readBytes().toString(Charset.defaultCharset())
        assertEquals(randomString, artifactFileString)
    }

    @Test
    fun testBigSizeInMemory() {
        val randomString = randomString(1024 * 1024)
        val source = randomString.byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, createMonitor(1024 * 1024 + 1))
        assertTrue(artifactFile.isInMemory())
        val artifactFileString = artifactFile.getInputStream().readBytes().toString(Charset.defaultCharset())
        assertEquals(randomString, artifactFileString)
    }

    @Test
    fun testBigSizeInFile() {
        val randomString = randomString(1024 * 1024)
        val source = randomString.byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, createMonitor(1024 * 1024 - 1))
        assertFalse(artifactFile.isInMemory())
        assertTrue(artifactFile.getFile()!!.exists())
        val artifactFileString = artifactFile.getInputStream().readBytes().toString(Charset.defaultCharset())
        assertEquals(randomString, artifactFileString)
    }

    @Test
    fun testDeleteInMemory() {
        val randomString = randomString(10)
        val source = randomString.byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, createMonitor(10))
        assertTrue(artifactFile.isInMemory())
        assertNull(artifactFile.getFile())
        artifactFile.delete()
    }

    @Test
    fun testDeleteInFile() {
        val randomString = randomString(11)
        val source = randomString.byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, createMonitor(10))
        assertFalse(artifactFile.isInMemory())
        assertTrue(artifactFile.getFile()!!.exists())
        artifactFile.delete()
        assertFalse(artifactFile.getFile()!!.exists())
    }

    @Test
    fun testFlushToFile() {
        val randomString = randomString(10)
        val source = randomString.byteInputStream()
        val artifactFile = OctetStreamArtifactFile(source, createMonitor(10))
        assertTrue(artifactFile.isInMemory())
        assertNull(artifactFile.getFile())

        val file = artifactFile.flushToFile()
        assertFalse(artifactFile.isInMemory())
        assertTrue(file.exists())
        assertEquals(file.absolutePath, artifactFile.getFile()!!.absolutePath)
        assertEquals(10, file.length())
        file.delete()
        assertFalse(file.exists())
        assertFalse(artifactFile.getFile()!!.exists())
    }
}
