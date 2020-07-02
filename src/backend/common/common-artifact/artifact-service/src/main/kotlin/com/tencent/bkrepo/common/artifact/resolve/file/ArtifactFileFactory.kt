package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.multipart.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.resolve.file.stream.OctetStreamArtifactFile
import com.tencent.bkrepo.common.artifact.util.ArtifactContextHolder
import com.tencent.bkrepo.common.storage.monitor.StorageHealthMonitor
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.multipart.MultipartFile
import java.io.InputStream

/**
 * ArtifactFileItem工厂方法
 *
 * @author: carrypan
 * @date: 2019/10/30
 */
@Component
class ArtifactFileFactory(
    storageHealthMonitor: StorageHealthMonitor
) {

    init {
        monitor = storageHealthMonitor
    }

    companion object {

        private lateinit var monitor: StorageHealthMonitor

        const val ARTIFACT_FILES = "artifact.files"

        fun build(inputStream: InputStream): ArtifactFile {
            val storageCredentials = ArtifactContextHolder.getRepositoryInfo()?.storageCredentials
            return OctetStreamArtifactFile(inputStream, monitor, storageCredentials).apply {
                track(this)
            }
        }

        fun build(multipartFile: MultipartFile): ArtifactFile {
            val storageCredentials = ArtifactContextHolder.getRepositoryInfo()?.storageCredentials
            return MultipartArtifactFile(multipartFile, monitor, storageCredentials).apply {
                track(this)
            }
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
