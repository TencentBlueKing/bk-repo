package com.tencent.bkrepo.common.artifact.api

import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.security.SecureRandom
import kotlin.math.abs

interface ArtifactFile {
    fun getInputStream(): InputStream
    fun getSize(): Long
    fun isInMemory(): Boolean
    fun getFile(): File?
    fun flushToFile(): File
    fun delete()

    companion object {
        protected const val ARTIFACT_PREFIX = "artifact_"
        protected const val ARTIFACT_SUFFIX = ".temp"
        protected val random = SecureRandom()

        fun generatePath(dir: Path): Path {
            var n = random.nextLong()
            n = if (n == Long.MIN_VALUE) 0 else abs(n)
            val path = dir.fileSystem.getPath(ARTIFACT_PREFIX + n.toString() + ARTIFACT_SUFFIX)
            require(path.parent == null) { "Invalid prefix or suffix" }
            return dir.resolve(path)
        }
    }
}
