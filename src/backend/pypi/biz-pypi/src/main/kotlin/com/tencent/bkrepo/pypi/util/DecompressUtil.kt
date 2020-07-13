package com.tencent.bkrepo.pypi.util

import com.tencent.bkrepo.pypi.exception.PypiUnSupportCompressException
import com.tencent.bkrepo.pypi.util.JsonUtil.jsonValue
import com.tencent.bkrepo.pypi.util.PropertiesUtil.propInfo
import com.tencent.bkrepo.pypi.util.pojo.PypiInfo
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.zip.GZIPInputStream

object DecompressUtil {

    private const val bufferSize = 2048

    /**
     * @param format 文件格式
     * @param this 待解压文件流
     * @return PypiInfo 包文件信息
     */
    @Throws(Exception::class)
    fun InputStream.getPkgInfo(format: String): PypiInfo {
        return when (format) {
            "tar" -> {
                getTarPkgInfo(this)
            }
            "whl" -> {
                getWhlMetadata(this)
            }
            "zip" -> {
                getZipMetadata(this)
            }
            "tar.gz", "tgz" -> {
                getTgzPkgInfo(this)
            }
            else -> {
                throw PypiUnSupportCompressException("Can not support compress format!")
            }
        }
    }

    @Throws(Exception::class)
    fun getWhlMetadata(inputStream: InputStream): PypiInfo {
        val metadata = getPkgInfo(ZipArchiveInputStream(inputStream), "metadata.json")
        return PypiInfo(metadata.jsonValue("name"), metadata.jsonValue("version"), metadata.jsonValue("summary"))
    }

    @Throws(Exception::class)
    fun getZipMetadata(inputStream: InputStream): PypiInfo {
        val propStr = getPkgInfo(ZipArchiveInputStream(inputStream), "PKG-INFO")
        return propStr.propInfo()
    }

    @Throws(Exception::class)
    fun getTgzPkgInfo(inputStream: InputStream): PypiInfo {
        val propStr = getPkgInfo(TarArchiveInputStream(GZIPInputStream(inputStream, 512)), "PKG-INFO")
        return propStr.propInfo()
    }

    @Throws(Exception::class)
    fun getTarPkgInfo(inputStream: InputStream): PypiInfo {
        val propStr = getPkgInfo(TarArchiveInputStream(inputStream), "PKG-INFO")
        return propStr.propInfo()
    }

    private fun getPkgInfo(archiveInputStream: ArchiveInputStream, file: String): String {
        val stringBuilder = StringBuffer("")
        archiveInputStream.use {
            try {
                while (archiveInputStream.nextEntry.also { zipEntry ->
                            zipEntry?.let {
                                if ((!zipEntry.isDirectory) && zipEntry.name.split("/").last() == file) {
                                    var length: Int
                                    val bytes = ByteArray(bufferSize)
                                    while ((archiveInputStream.read(bytes).also { length = it }) != -1) {
                                        stringBuilder.append(String(bytes, 0, length))
                                    }
                                }
                            }
                        } != null) {}
            } catch (ise: IllegalStateException) {
                if (ise.message != "it must not be null") {
                } else {
                    logger.error(ise.message)
                }
            }
        }
        return stringBuilder.toString()
    }

    private val logger = LoggerFactory.getLogger(DecompressUtil::class.java)
}
