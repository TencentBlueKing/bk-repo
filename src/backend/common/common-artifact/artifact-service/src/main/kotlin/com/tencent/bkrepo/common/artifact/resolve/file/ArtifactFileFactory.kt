package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.multipart.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.stream.OctetStreamArtifactFile
import com.tencent.bkrepo.common.security.manager.ArtifactContextHolder
import com.tencent.bkrepo.common.storage.core.StorageProperties
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream

/**
 * ArtifactFile工厂方法
 */
@Component
class ArtifactFileFactory(
    storageProperties: StorageProperties,
    storageHealthMonitor: StorageHealthMonitor
) {

    init {
        monitor = storageHealthMonitor
        properties = storageProperties
    }

    companion object {

        private lateinit var monitor: StorageHealthMonitor
        private lateinit var properties: StorageProperties

        const val ARTIFACT_FILES = "artifact.files"

        fun build(inputStream: InputStream): ArtifactFile {
            return OctetStreamArtifactFile(inputStream, monitor, properties, getStorageCredentials()).apply {
                track(this)
            }
        }

        fun build(multipartFile: MultipartFile): ArtifactFile {
            return MultipartArtifactFile(multipartFile, monitor, properties, getStorageCredentials()).apply {
                track(this)
            }
        }

        private fun getStorageCredentials(): StorageCredentials {
            return ArtifactContextHolder.getRepositoryInfo()?.storageCredentials ?: properties.defaultStorageCredentials()
        }

        @Suppress("UNCHECKED_CAST")
        private fun track(artifactFile: ArtifactFile) {
            val requestAttributes = RequestContextHolder.getRequestAttributes()
            if (requestAttributes != null) {
                var artifactFileList = requestAttributes.getAttribute(ARTIFACT_FILES, SCOPE_REQUEST) as? MutableList<ArtifactFile>
                if (artifactFileList == null) {
                    artifactFileList = mutableListOf()
                    RequestContextHolder.getRequestAttributes()?.setAttribute(ARTIFACT_FILES, artifactFileList, SCOPE_REQUEST)
                }
                artifactFileList.add(artifactFile)
            }
        }
    }
}
