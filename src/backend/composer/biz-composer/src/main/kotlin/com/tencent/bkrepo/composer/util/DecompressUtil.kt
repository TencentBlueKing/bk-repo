package com.tencent.bkrepo.composer.util

import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.slf4j.LoggerFactory
import java.io.*
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object DecompressUtil {

    private const val BUFFER_SIZE = 2048

    /**
     *
     */
    @Throws(Exception::class)
    private fun unTar(inputStream: InputStream, destDir: String) {
        TarArchiveInputStream(inputStream, BUFFER_SIZE).use { outer ->
            var entry: TarArchiveEntry
            try {
                while ((outer.nextTarEntry).also {entry = it} != null) {
                    if (entry.isDirectory)  // 是目录
                        createDirectory(destDir, entry.name) // 创建空目录
                    else { // 是文件
                        val tmpFile = File(destDir + File.separator + entry.name)
                        createDirectory(tmpFile.parent + File.separator, null) // 创建输出目录
                        FileOutputStream(tmpFile).use { inner ->
                            var length: Int
                            val b = ByteArray(2048)
                            while ((outer.read(b).also { length = it }) != -1) {
                                inner.write(b, 0, length)
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
    }

    @Throws(Exception::class)
    infix fun ZipInputStream.unZipTo(destDir: String) {
        try {
            while (nextEntry.also { it?.let {
                        if (it.isDirectory) {
                            File("$destDir/${it.name}").mkdirs()
                        } else {
                            val tmpFile = File(destDir + File.separator + it.name)
                            createDirectory(tmpFile.parent + File.separator, null)
                            FileOutputStream(tmpFile).use { fos ->
                                var length: Int
                                val b = ByteArray(2048)
                                while ((this.read(b).also { length = it }) != -1) {
                                    fos.write(b, 0, length)
                                }
                            }
                        }
                    } } != null) {}
        } catch (ise: IllegalStateException) {
            if(ise.message != "it must not be null")
                logger.error(ise.message)
        }
    }

    @Throws(Exception::class)
    fun getComposerJson(inputStream: InputStream, format: String): String {
        return when (format) {
            "tar" -> {
                getTarComposerJson(inputStream)
            }
            "zip" -> {
                getZipComposerJson(inputStream)
            }
            "tar.gz" -> {
                getTgzComposerJson(inputStream)
            }
            else -> {
                "can not support compress format!"
            }
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

//fun main() {
////    ZipFile("/Users/weaving/Downloads/jetbrains-agent-latest.zip") unZipTo "/Users/weaving/Downloads/"
////    ZipInputStream(FileInputStream("/Users/weaving/Downloads/jetbrains-agent-latest.zip")) unZipTo "/Users/weaving/Downloads/"
////    ZipInputStream(FileInputStream("/Users/weaving/Downloads/monolog-2.0.2.zip")) unZipTo "/Users/weaving/Downloads/"
//    print(DecompressUtil.getTgzComposerJson(FileInputStream("/Users/weaving/Downloads/monolog-2.1.0.tar.gz")))
//    print(DecompressUtil.getTarComposerJson(FileInputStream("/Users/weaving/Downloads/monolog-2.0.7.tar")))
//    print(DecompressUtil.getZipComposerJson(FileInputStream("/Users/weaving/Downloads/monolog-2.0.2.zip")))
//}
