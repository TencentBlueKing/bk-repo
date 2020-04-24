package com.tencent.bkrepo.composer.util

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tencent.bkrepo.composer.util.JsonUtil.jsonValue
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

object DecompressUtil {

    @Throws(Exception::class)
    fun InputStream.getComposerJson(format: String): String {
        return when (format) {
            "tar" -> {
                getTarComposerJson(this)
            }
            "zip" -> {
                getZipComposerJson(this)
            }
            "tar.gz" -> {
                getTgzComposerJson(this)
            }
            "tgz" -> {
                getTgzComposerJson(this)
            }
            else -> {
                "can not support compress format!"
            }
        }
    }

    @Throws(Exception::class)
    fun InputStream.wrapperJson(uri: String): Map<String, String>?{
        UriUtil.getUriArgs(uri)?.let { args ->
            args["format"]?.let { format ->
                this.getComposerJson(format).let{ json ->
                    JsonParser().parse(json).asJsonObject.let {
                        it.addProperty("uid", UUID.randomUUID().toString())
                        val distObject = JsonObject()
                        distObject.addProperty("type",format)
                        distObject.addProperty("url","direct-dists$uri")
                        it.add("dist", distObject)
                        it.addProperty("type", "library")
                        return mapOf("packageName" to (json jsonValue "name"),
                                "version" to (json jsonValue "version"),
                                "json" to GsonBuilder().create().toJson(it))
                    }
                }
            }
        }
        return null
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

    private fun getCompressComposerJson(tarInputStream: ArchiveInputStream): String {
        val stringBuilder = StringBuffer("")
        with(tarInputStream) {
            try {
                while (nextEntry.also { zipEntry ->
                            zipEntry?.let {
                                if ((!zipEntry.isDirectory) && zipEntry.name.split("/").last() == com.tencent.bkrepo.composer.COMPOSER_JSON) {
                                    var length: Int
                                    val bytes = kotlin.ByteArray(2048)
                                    while ((tarInputStream.read(bytes).also { length = it }) != -1) {
                                        stringBuilder.append(kotlin.text.String(bytes, 0, length))
                                    }
                                    return stringBuilder.toString()
                                }
                            }
                        } != null);
            } catch (ise: IllegalStateException) {
                if (ise.message != "it must not be null") {
                }else{
                    logger.error(ise.message)
                }
            }
        }
        return stringBuilder.toString()
    }

    /**
     * @param destDir
     */
    @Throws(Exception::class)
    infix fun ZipFile.unZipTo(destDir: String) {
        try {
            for (entry in entries()) {
                //判断是否为文件夹
                if (entry.isDirectory) {
                    File("$destDir/${entry.name}").mkdirs()
                } else {
                    getInputStream(entry).use { entryStream ->
                        val tmpFile = File(destDir + File.separator + entry.name)
                        createDirectory(tmpFile.parent + File.separator, null)
                        FileOutputStream(tmpFile).use { fos ->
                            var length: Int
                            val b = ByteArray(2048)
                            while ((entryStream.read(b).also { length = it }) != -1) {
                                fos.write(b, 0, length)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logger.error("file unTar failed : " + e.message)
            throw e
        }
    }

    private fun createDirectory(outputDir: String, subDir: String?) {
        var file = File(outputDir)
        if (!(subDir == null || subDir.trim { it <= ' ' } == "")) { // 子目录不为空
            file = File(outputDir + File.separator + subDir)
        }
        if (!file.exists()) {
            file.mkdirs()
        }
    }

    private val logger = LoggerFactory.getLogger(DecompressUtil::class.java)
}
