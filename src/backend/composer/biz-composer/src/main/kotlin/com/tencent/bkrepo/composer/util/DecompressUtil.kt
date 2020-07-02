package com.tencent.bkrepo.composer.util

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tencent.bkrepo.composer.COMPOSER_JSON
import com.tencent.bkrepo.composer.exception.ComposerUnSupportCompressException
import com.tencent.bkrepo.composer.util.JsonUtil.jsonValue
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.zip.GZIPInputStream
import com.tencent.bkrepo.composer.util.pojo.ComposerJsonNode
import java.util.*

object DecompressUtil {

    private const val bufferSize = 2048

    /**
     * 获取composer 压缩包中'composer.json'文件
     * @param InputStream 文件流
     * @param format 文件压缩格式
     * @exception
     */
    @Throws(Exception::class)
    fun InputStream.getComposerJson(format: String): String {
        return when (format) {
            "tar" -> {
                getTarComposerJson(this)
            }
            "zip", "whl" -> {
                getZipComposerJson(this)
            }
            "tar.gz", "tgz" -> {
                getTgzComposerJson(this)
            }
            else -> {
                throw ComposerUnSupportCompressException("Can not support compress format!")
            }
        }
    }

    /**
     * composer package 中'composer.json'添加到服务器上对应%package%.json是需要增加一些信息
     * @param InputStream composer package 的文件流
     * @param uri 请求中的全文件名
     * @return 'packageName': 包名; 'version': 版本; 'json': 增加属性后的json内容
     */
    @Throws(Exception::class)
    fun InputStream.wrapperJson(uri: String): ComposerJsonNode {
        val uriArgs = UriUtil.getUriArgs(uri)
        val json = this.getComposerJson(uriArgs.format)
        JsonParser().parse(json).asJsonObject.let {
            // Todo uid的值从什么地方拿
            it.addProperty("uid", UUID.randomUUID().toString())
            val distObject = JsonObject()
            distObject.addProperty("type", uriArgs.format)
            distObject.addProperty("url", "direct-dists$uri")
            it.add("dist", distObject)
            it.addProperty("type", "library")
            return ComposerJsonNode((json jsonValue "name"), (json jsonValue "version"), GsonBuilder().create().toJson(it))
        }
    }

    @Throws(Exception::class)
    fun getZipComposerJson(inputStream: InputStream): String {
        return getCompressComposerJson(ZipArchiveInputStream(inputStream))
    }

    @Throws(Exception::class)
    fun getTgzComposerJson(inputStream: InputStream): String {
        return getCompressComposerJson(TarArchiveInputStream(GZIPInputStream(inputStream)))
    }

    @Throws(Exception::class)
    fun getTarComposerJson(inputStream: InputStream): String {
        return getCompressComposerJson(TarArchiveInputStream(inputStream))
    }

    /**
     * 获取Composer package 压缩中的'composer.json'文件
     * @param tarInputStream 压缩文件流
     * @return 以字符串格式返回 composer.json 文件内容
     */
    private fun getCompressComposerJson(archiveInputStream: ArchiveInputStream): String {
        val stringBuilder = StringBuffer("")
        archiveInputStream.use {
            try {
                while (archiveInputStream.nextEntry.also { zipEntry ->
                            zipEntry?.let {
                                if ((!zipEntry.isDirectory) && zipEntry.name.split("/").last() == COMPOSER_JSON) {
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
