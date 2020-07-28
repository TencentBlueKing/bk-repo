package com.tencent.bkrepo.pypi.util

import com.tencent.bkrepo.pypi.exception.PypiUnSupportCompressException
import com.tencent.bkrepo.pypi.util.JsonUtil.jsonValue
import com.tencent.bkrepo.pypi.util.PropertiesUtil.propInfo
import com.tencent.bkrepo.pypi.util.pojo.PypiInfo
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

object DecompressUtil {

    private const val bufferSize = 2048
    // 支持的压缩格式
    private const val TAR = "tar"
    private const val ZIP = "zip"
    private const val WHL = "whl"
    private const val GZ = "tar.gz"
    private const val TGZ = "tgz"

    // 目标属性
    private const val name = "name"
    private const val version = "version"
    private const val summary = "summary"

    // 目标文件
    private const val metadata = "metadata.json"
    private const val pkgInfo = "PKG-INFO"

    /**
     * @param format 文件格式
     * @param this 待解压文件流
     * @return PypiInfo 包文件信息
     */
    fun InputStream.getPkgInfo(format: String): PypiInfo {
        return when (format) {
            TAR -> {
                getTarPkgInfo(this)
            }
            WHL -> {
                getWhlMetadata(this)
            }
            ZIP -> {
                getZipMetadata(this)
            }
            GZ, TGZ -> {
                getTgzPkgInfo(this)
            }
            else -> {
                throw PypiUnSupportCompressException("Can not support compress format!")
            }
        }
    }

    private fun getWhlMetadata(inputStream: InputStream): PypiInfo {
        val metadata = getPkgInfo(ZipArchiveInputStream(inputStream), metadata)
        return PypiInfo(metadata jsonValue name, metadata jsonValue version, metadata jsonValue summary)
    }

    private fun getZipMetadata(inputStream: InputStream): PypiInfo {
        val propStr = getPkgInfo(ZipArchiveInputStream(inputStream), pkgInfo)
        return propStr.propInfo()
    }

    private fun getTgzPkgInfo(inputStream: InputStream): PypiInfo {
        val propStr = getPkgInfo(TarArchiveInputStream(GZIPInputStream(inputStream, 512)), pkgInfo)
        return propStr.propInfo()
    }

    private fun getTarPkgInfo(inputStream: InputStream): PypiInfo {
        val propStr = getPkgInfo(TarArchiveInputStream(inputStream), pkgInfo)
        return propStr.propInfo()
    }

    private fun getPkgInfo(archiveInputStream: ArchiveInputStream, file: String): String {
        val stringBuilder = StringBuffer("")
        var zipEntry: ArchiveEntry
        archiveInputStream.use {
            while (archiveInputStream.nextEntry.also { zipEntry = it } != null) {
                if ((!zipEntry.isDirectory) && zipEntry.name.split("/").last() == file) {
                    var length: Int
                    val bytes = ByteArray(bufferSize)
                    while ((archiveInputStream.read(bytes).also { length = it }) != -1) {
                        stringBuilder.append(String(bytes, 0, length))
                    }
                }
            }
        }
        return stringBuilder.toString()
    }
}
