package com.tencent.bkrepo.common.artifact.metrics

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.constant.DEFAULT_STORAGE_KEY
import com.tencent.bkrepo.common.artifact.constant.SOURCE_IN_MEMORY
import com.tencent.bkrepo.common.artifact.constant.SOURCE_IN_REMOTE
import com.tencent.bkrepo.common.artifact.resolve.file.stream.CosStreamArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.FileArtifactInputStream
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import org.slf4j.LoggerFactory
import java.io.File

/**
 * 传输介质标签推导，与 [DefaultArtifactTagProvider] 中 path 标签语义一致。
 */
object TransferMedium {
    private val logger = LoggerFactory.getLogger(TransferMedium::class.java)
    private const val STORAGE_KEY_SEPARATOR = "::"

    /** 上传场景：ArtifactFile -> medium 取值 */
    fun of(artifactFile: ArtifactFile, credentials: StorageCredentials?): String {
        return tagPath(credentials, rawPath(artifactFile))
    }

    /** 下载场景：ArtifactResource -> medium 取值 */
    fun of(resource: ArtifactResource, credentials: StorageCredentials?): String {
        val rawPaths = resource.artifactMap.values.map { rawPath(it) }.toSet()
        val raw = rawPaths.singleOrNull() ?: SOURCE_IN_REMOTE
        return tagPath(credentials, raw)
    }

    /**
     * 推导 medium / path 标签值。memory、remote 及本地路径均带 `::storageKey` 后缀。
     */
    fun tagPath(credentials: StorageCredentials?, path: String): String {
        val storageKey = credentials?.key ?: DEFAULT_STORAGE_KEY
        if (path == SOURCE_IN_MEMORY) {
            return "$SOURCE_IN_MEMORY$STORAGE_KEY_SEPARATOR$storageKey"
        }
        if (path == SOURCE_IN_REMOTE) {
            return "$SOURCE_IN_REMOTE$STORAGE_KEY_SEPARATOR$storageKey"
        }
        credentials ?: return StringPool.UNKNOWN
        with(credentials) {
            // 先匹配更具体的路径，且用目录边界避免 upload-local 被误判为 upload 子路径
            if (isUnderPath(path, upload.localPath)) {
                return withStorageKey(upload.localPath, storageKey)
            }
            if (isUnderPath(path, cache.path)) {
                return withStorageKey(cache.path, storageKey)
            }
            if (isUnderPath(path, upload.location)) {
                return withStorageKey(upload.location, storageKey)
            }
            logger.warn("Unknown path[$path] origin with key[${credentials.key}]")
            return StringPool.UNKNOWN
        }
    }

    private fun withStorageKey(path: String, storageKey: String): String =
        "$path$STORAGE_KEY_SEPARATOR$storageKey"

    /**
     * 判断 [path] 是否位于 [base] 目录下（含 base 自身），要求 base 后紧跟路径分隔符，
     * 避免 upload 与 upload-local 等前缀相近路径误判。
     */
    internal fun isUnderPath(path: String, base: String): Boolean {
        if (path == base) {
            return true
        }
        val normalized = base.trimEnd(File.separatorChar, '/')
        return path.startsWith(normalized + File.separatorChar)
    }

    private fun rawPath(artifactFile: ArtifactFile): String = when {
        artifactFile is CosStreamArtifactFile -> SOURCE_IN_REMOTE
        artifactFile.isInMemory() -> SOURCE_IN_MEMORY
        else -> runCatching { artifactFile.getFile()?.path }
            .getOrNull() ?: SOURCE_IN_REMOTE
    }

    private fun rawPath(stream: ArtifactInputStream): String =
        if (stream is FileArtifactInputStream) stream.file.path else SOURCE_IN_REMOTE
}
