package com.tencent.bkrepo.pypi.util

import com.tencent.bkrepo.pypi.util.JsonUtil.jsonValue
import com.tencent.bkrepo.pypi.util.PropertiesUtil.propInfo
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.zip.GZIPInputStream

object DecompressUtil {

    /**
     * @param format 文件格式
     * @param this 待解压文件流
     * @return pypi 包文件信息
     */
    @Throws(Exception::class)
    fun InputStream.getPkgInfo(format: String): Map<String, String> {
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
            "tar.gz","tgz" -> {
                getTgzPkgInfo(this)
            }
            else -> {
                mapOf()
            }
        }
    }

    @Throws(Exception::class)
    fun getWhlMetadata(inputStream: InputStream): Map<String, String> {
        val metadata = getPkgInfo(ZipArchiveInputStream(inputStream), "metadata.json")
        return if (metadata.isBlank()) {
            mapOf()
        } else {
            return mapOf("name" to metadata.jsonValue("name"),
                    "version" to metadata.jsonValue("version"),
                    "summary" to metadata.jsonValue("summary"))
        }
    }

    @Throws(Exception::class)
    fun getZipMetadata(inputStream: InputStream): Map<String, String> {
        val propStr = getPkgInfo(ZipArchiveInputStream(inputStream), "PKG-INFO")
        return propStr.propInfo()
    }

    @Throws(Exception::class)
    fun getTgzPkgInfo(inputStream: InputStream): Map<String, String> {
        val propStr = getPkgInfo(TarArchiveInputStream(GZIPInputStream(inputStream, 512)), "PKG-INFO")
        return propStr.propInfo()
    }

    @Throws(Exception::class)
    fun getTarPkgInfo(inputStream: InputStream): Map<String, String> {
        val propStr = getPkgInfo(TarArchiveInputStream(inputStream), "PKG-INFO")
        return propStr.propInfo()
    }



    private fun getPkgInfo(tarInputStream: ArchiveInputStream, file: String): String {
        val stringBuilder = StringBuffer("")
        with(tarInputStream) {
            try {
                while (nextEntry.also { zipEntry ->
                            zipEntry?.let {
                                if ((!zipEntry.isDirectory) && zipEntry.name.split("/").last() == file) {
                                    var length: Int
                                    val bytes = ByteArray(2048)
                                    while ((tarInputStream.read(bytes).also { length = it }) != -1) {
                                        stringBuilder.append(String(bytes, 0, length))
                                    }
                                    return stringBuilder.toString()
                                }
                            }
                        } != null){}
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
