package com.tencent.bkrepo.common.artifact.api

import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import kotlin.math.abs

interface ArtifactFile {
    fun getInputStream(): InputStream
    fun getSize(): Long
    fun isInMemory(): Boolean
    fun getFile(): File?
    fun flushToFile(): File
    fun isFallback(): Boolean
    fun getFileMd5(): String
    fun getFileSha256(): String
    fun delete()
    fun hasInitialized(): Boolean

    companion object {
        protected const val ARTIFACT_PREFIX = "artifact_"
        protected const val ARTIFACT_SUFFIX = ".temp"
        protected val random = SecureRandom()
        fun generateRandomName(): String {
            var randomLong = random.nextLong()
            randomLong = if (randomLong == Long.MIN_VALUE) 0 else abs(randomLong)
            return ARTIFACT_PREFIX + randomLong.toString() + ARTIFACT_SUFFIX
        }
    }
}
