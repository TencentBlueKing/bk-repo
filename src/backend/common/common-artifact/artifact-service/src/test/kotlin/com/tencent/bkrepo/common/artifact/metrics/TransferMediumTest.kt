package com.tencent.bkrepo.common.artifact.metrics

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.constant.SOURCE_IN_MEMORY
import com.tencent.bkrepo.common.artifact.constant.SOURCE_IN_REMOTE
import com.tencent.bkrepo.common.artifact.resolve.file.stream.CosStreamArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.FileArtifactInputStream
import com.tencent.bkrepo.common.storage.config.CacheProperties
import com.tencent.bkrepo.common.storage.config.UploadProperties
import com.tencent.bkrepo.common.storage.credentials.FileSystemCredentials
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class TransferMediumTest {

    private fun credentials(key: String = "cos-key"): FileSystemCredentials {
        val uploadLocation = File("data", "store/upload").absolutePath
        val uploadLocalPath = File("data", "store/upload-local").absolutePath
        val cachePath = File("data", "store/cache").absolutePath
        return FileSystemCredentials(
            key = key,
            upload = UploadProperties(location = uploadLocation, localPath = uploadLocalPath),
            cache = CacheProperties(path = cachePath),
        )
    }

    @Test
    fun `tagPath returns memory and remote with storage key`() {
        val cred = credentials("my-key")
        assertEquals("memory::my-key", TransferMedium.tagPath(cred, SOURCE_IN_MEMORY))
        assertEquals("remote::my-key", TransferMedium.tagPath(cred, SOURCE_IN_REMOTE))
        assertEquals("memory::default", TransferMedium.tagPath(null, SOURCE_IN_MEMORY))
    }

    @Test
    fun `tagPath matches upload location local path and cache path with storage key`() {
        val cred = credentials()
        assertEquals(
            "${cred.upload.location}::${cred.key}",
            TransferMedium.tagPath(cred, File(cred.upload.location, "file.dat").path),
        )
        assertEquals(
            "${cred.upload.localPath}::${cred.key}",
            TransferMedium.tagPath(cred, File(cred.upload.localPath, "small.dat").path),
        )
        assertEquals(
            "${cred.cache.path}::${cred.key}",
            TransferMedium.tagPath(cred, File(cred.cache.path, "artifact.bin").path),
        )
    }

    @Test
    fun `isUnderPath does not treat upload-local as under upload`() {
        val cred = credentials()
        val underLocal = File(cred.upload.localPath, "small.dat").path
        val underUpload = File(cred.upload.location, "file.dat").path
        assertEquals(false, TransferMedium.isUnderPath(underLocal, cred.upload.location))
        assertEquals(true, TransferMedium.isUnderPath(underLocal, cred.upload.localPath))
        assertEquals(true, TransferMedium.isUnderPath(underUpload, cred.upload.location))
    }

    @Test
    fun `tagPath returns unknown for null credentials or unknown path`() {
        assertEquals(StringPool.UNKNOWN, TransferMedium.tagPath(null, "/other/path"))
        assertEquals(StringPool.UNKNOWN, TransferMedium.tagPath(credentials(), "/unknown/path"))
    }

    @Test
    fun `of ArtifactFile maps cos stream to remote`() {
        val cosFile = mockk<CosStreamArtifactFile>()
        assertEquals("remote::test", TransferMedium.of(cosFile, credentials("test")))
    }

    @Test
    fun `of ArtifactFile maps in memory to memory`() {
        val file = mockk<ArtifactFile>()
        every { file.isInMemory() } returns true
        assertEquals("memory::default", TransferMedium.of(file, null))
    }

    @Test
    fun `of ArtifactFile maps disk file path via upload location`() {
        val cred = credentials()
        val diskFile = File(cred.upload.location, "received.dat")
        val file = mockk<ArtifactFile>()
        every { file.isInMemory() } returns false
        every { file.getFile() } returns diskFile
        assertEquals("${cred.upload.location}::${cred.key}", TransferMedium.of(file, cred))
    }

    @Test
    fun `of ArtifactFile falls back to remote when getFile throws`() {
        val file = mockk<ArtifactFile>()
        every { file.isInMemory() } returns false
        every { file.getFile() } throws UnsupportedOperationException()
        assertEquals("remote::default", TransferMedium.of(file, null))
    }

    @Test
    fun `of ArtifactResource maps file stream to cache path`() {
        val cred = credentials()
        val stream = mockk<FileArtifactInputStream>(relaxed = true)
        every { stream.file } returns File(cred.cache.path, "node.bin")
        val resource = ArtifactResource(mapOf("a" to stream))
        assertEquals("${cred.cache.path}::${cred.key}", TransferMedium.of(resource, cred))
    }

    @Test
    fun `of ArtifactResource maps remote stream`() {
        val stream = mockk<ArtifactInputStream>()
        val resource = ArtifactResource(mapOf("a" to stream))
        assertEquals("remote::default", TransferMedium.of(resource, null))
    }

    @Test
    fun `of ArtifactResource uses remote for mixed or empty streams`() {
        val cred = credentials()
        val fileStream = mockk<FileArtifactInputStream>(relaxed = true)
        every { fileStream.file } returns File(cred.cache.path, "a")
        val remoteStream = mockk<ArtifactInputStream>()
        val mixed = ArtifactResource(mapOf("a" to fileStream, "b" to remoteStream))
        assertEquals("remote::default", TransferMedium.of(mixed, null))

        val empty = ArtifactResource(emptyMap())
        assertEquals("remote::${cred.key}", TransferMedium.of(empty, cred))
    }
}
