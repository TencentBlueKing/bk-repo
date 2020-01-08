package com.tencent.bkrepo.common.artifact.file

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import org.apache.commons.fileupload.disk.DiskFileItem
import org.apache.commons.fileupload.servlet.FileCleanerCleanup
import org.apache.commons.io.FileCleaningTracker
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * ArtifactFileItem工厂方法
 *
 * @author: carrypan
 * @date: 2019/10/30
 */
object ArtifactFileFactory {
    private const val defaultSizeThreshold: Int = 1024 * 1024

    fun build(sizeThreshold: Int = defaultSizeThreshold): ArtifactFile {
        val artifactFile = OctetStreamArtifactFile(sizeThreshold)
        val tracker = getFileCleaningTracker()
        tracker?.track(artifactFile.getTempFile(), artifactFile)
        return artifactFile
    }

    fun adapt(diskFileItem: DiskFileItem): ArtifactFile {
        return MultipartArtifactFile(diskFileItem)
    }

    private fun getFileCleaningTracker(): FileCleaningTracker? {
        val context = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request?.servletContext
        return context?.run {
            var tracker = this.getAttribute(FileCleanerCleanup.FILE_CLEANING_TRACKER_ATTRIBUTE) as? FileCleaningTracker
            if (tracker == null) {
                tracker = FileCleaningTracker()
                this.setAttribute(FileCleanerCleanup.FILE_CLEANING_TRACKER_ATTRIBUTE, tracker)
            }
            return tracker
        }
    }
}
