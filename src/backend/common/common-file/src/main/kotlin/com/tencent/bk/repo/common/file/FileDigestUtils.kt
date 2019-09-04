package com.tencent.bk.repo.common.file

import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

object FileDigestUtils {

    @Throws(IOException::class, IllegalArgumentException::class)
    fun fileSha1(inputFiles: Array<String>): String? {
        return digest(inputFiles, DigestUtils.getSha1Digest())
    }

    @Throws(IOException::class, IllegalArgumentException::class)
    fun fileMD5(inputFiles: Array<String>): String? {
        return digest(inputFiles, DigestUtils.getMd5Digest())
    }

    @Throws(IOException::class, IllegalArgumentException::class)
    fun fileSha256(inputFiles: Array<String>): String? {
        return digest(inputFiles, DigestUtils.getSha256Digest())
    }

    private fun digest(inputFiles: Array<String>, messageDigest: MessageDigest): String? {
        inputFiles.forEach { inputFile ->
            FileInputStream(inputFile).use { inputStream ->
                DigestUtils.updateDigest(messageDigest, inputStream)
            }
        }
        return Hex.encodeHexString(messageDigest.digest())
    }
}