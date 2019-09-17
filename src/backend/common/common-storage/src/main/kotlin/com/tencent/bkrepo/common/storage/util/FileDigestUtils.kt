package com.tencent.bkrepo.common.storage.util

import java.io.IOException
import java.security.MessageDigest
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import java.io.InputStream

object FileDigestUtils {

    @Throws(IOException::class, IllegalArgumentException::class)
    fun fileSha1(inputStreamList: List<InputStream>): String {
        return digest(inputStreamList, DigestUtils.getSha1Digest())
    }

    @Throws(IOException::class, IllegalArgumentException::class)
    fun fileMD5(inputStreamList: List<InputStream>): String {
        return digest(inputStreamList, DigestUtils.getMd5Digest())
    }

    @Throws(IOException::class, IllegalArgumentException::class)
    fun fileSha256(inputStreamList: List<InputStream>): String {
        return digest(inputStreamList, DigestUtils.getSha256Digest())
    }

    private fun digest(inputStreamList: List<InputStream>, messageDigest: MessageDigest): String {
        inputStreamList.forEach { DigestUtils.updateDigest(messageDigest, it) }
        return Hex.encodeHexString(messageDigest.digest())
    }
}
