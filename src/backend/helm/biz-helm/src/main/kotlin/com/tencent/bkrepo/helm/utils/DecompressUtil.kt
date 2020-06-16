package com.tencent.bkrepo.helm.utils

import com.tencent.bkrepo.helm.constants.CHART_YAML
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.zip.GZIPInputStream

object DecompressUtil {
    private val logger = LoggerFactory.getLogger(DecompressUtil::class.java)
    private const val BUFFER_SIZE = 2048
    private const val FILE_NAME = CHART_YAML

    @Throws(Exception::class)
    fun InputStream.getArchivesContent(format: String): String {
        return when (format) {
            "tar" -> {
                getTarArchiversContent(this)
            }
            "zip" -> {
                getZipArchiversContent(this)
            }
            "tar.gz" -> {
                getTgzArchiversContent(this)
            }
            "tgz" -> {
                getTgzArchiversContent(this)
            }
            else -> {
                "can not support compress format!"
            }
        }
    }

    @Throws(Exception::class)
    fun getZipArchiversContent(inputStream: InputStream): String {
        return getArchiversContent(ZipArchiveInputStream(inputStream))
    }

    @Throws(Exception::class)
    fun getTgzArchiversContent(inputStream: InputStream): String {
        return getArchiversContent(TarArchiveInputStream(GZIPInputStream(inputStream)))
    }

    @Throws(Exception::class)
    fun getTarArchiversContent(inputStream: InputStream): String {
        return getArchiversContent(TarArchiveInputStream(inputStream))
    }

    private fun getArchiversContent(archiveInputStream: ArchiveInputStream): String {
        val stringBuilder = StringBuffer()
        archiveInputStream.use {
            try {
                while (it.nextEntry.also { zipEntry ->
                        zipEntry?.let {
                            if ((!zipEntry.isDirectory) && zipEntry.name.split("/").last() == FILE_NAME) {
                                var length: Int
                                val bytes = ByteArray(BUFFER_SIZE)
                                while ((archiveInputStream.read(bytes).also { length = it }) != -1) {
                                    stringBuilder.append(String(bytes, 0, length))
                                }
                                return stringBuilder.toString()
                            }
                        }
                    } != null) {
                }
            } catch (ise: IllegalStateException) {
                logger.error(ise.message)
            }
        }
        return stringBuilder.toString()
    }
}
