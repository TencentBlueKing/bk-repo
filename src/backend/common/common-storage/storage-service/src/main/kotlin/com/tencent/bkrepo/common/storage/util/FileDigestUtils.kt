package com.tencent.bkrepo.common.storage.util

import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

object FileDigestUtils {

    @Throws(IOException::class, IllegalArgumentException::class)
    fun fileSha1(inputStreamList: List<InputStream>): String {
        return digest(inputStreamList, DigestUtils.getSha1Digest())
    }

    @Throws(IOException::class, IllegalArgumentException::class)
    fun fileMd5(file: File): String {
        return digest(listOf(file.inputStream()), DigestUtils.getMd5Digest())
    }

    @Throws(IOException::class, IllegalArgumentException::class)
    fun fileMd5(inputStream: InputStream): String {
        return digest(listOf(inputStream), DigestUtils.getMd5Digest())
    }

    @Throws(IOException::class, IllegalArgumentException::class)
    fun fileMd5(inputStreamList: List<InputStream>): String {
        return digest(inputStreamList, DigestUtils.getMd5Digest())
    }

    @Throws(IOException::class, IllegalArgumentException::class)
    fun fileSha256(file: File): String {
        return digest(listOf(file.inputStream()), DigestUtils.getSha1Digest())
    }

    @Throws(IOException::class, IllegalArgumentException::class)
    fun fileSha256(inputStream: InputStream): String {
        return digest(listOf(inputStream), DigestUtils.getSha256Digest())
    }

    @Throws(IOException::class, IllegalArgumentException::class)
    fun fileSha256(inputStreamList: List<InputStream>): String {
        return digest(inputStreamList, DigestUtils.getSha256Digest())
    }

    private fun digest(inputStreamList: List<InputStream>, messageDigest: MessageDigest): String {
        inputStreamList.forEach { it.use { inputStream -> DigestUtils.updateDigest(messageDigest, inputStream) } }
        return Hex.encodeHexString(messageDigest.digest())
    }
}
