package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import org.apache.commons.fileupload.disk.DiskFileItem
import org.apache.commons.io.FileCleaningTracker
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties
import org.springframework.stereotype.Component
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

        fun build(inputStream: InputStream): ArtifactFile {
            val artifactFile = OctetStreamArtifactFile(inputStream, config.location, config.fileSizeThreshold, config.resolveLazily)
            fileCleaningTracker.track(artifactFile.getFile(), artifactFile)
            return artifactFile
        }

        fun build(diskFileItem: DiskFileItem): ArtifactFile {
            return MultipartArtifactFile(diskFileItem)
        }
    }
}
