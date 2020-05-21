package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import org.apache.commons.fileupload.disk.DiskFileItem
import org.apache.commons.io.FileCleaningTracker
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST
import org.springframework.web.context.request.RequestContextHolder
import java.io.InputStream

/**
 * ArtifactFileItem工厂方法
 *
 * @author: carrypan
 * @date: 2019/10/30
 */
@Component
class ArtifactFileFactory(multipartProperties: MultipartProperties) {

    init {
        config = UploadConfigElement(multipartProperties)
    }


    companion object {

        private lateinit var config: UploadConfigElement
        private val fileCleaningTracker = FileCleaningTracker()
        const val ARTIFACT_FILES = "artifact.files"

        fun build(inputStream: InputStream): ArtifactFile {
            val artifactFile = OctetStreamArtifactFile(inputStream, config.location, config.fileSizeThreshold, config.resolveLazily)

            track(artifactFile)
            return artifactFile
        }

        fun build(diskFileItem: DiskFileItem): ArtifactFile {
            return MultipartArtifactFile(diskFileItem)
        }

        private fun track(artifactFile: ArtifactFile) {
            fileCleaningTracker.track(artifactFile.getFile(), artifactFile)

            var artifactFileList = RequestContextHolder.getRequestAttributes()?.getAttribute(ARTIFACT_FILES, SCOPE_REQUEST) as? MutableList<ArtifactFile>
            if (artifactFileList == null) {
                artifactFileList = mutableListOf()
                RequestContextHolder.getRequestAttributes()?.setAttribute(ARTIFACT_FILES, artifactFileList, SCOPE_REQUEST)
            }
            artifactFileList.add(artifactFile)
        }
    }
}
