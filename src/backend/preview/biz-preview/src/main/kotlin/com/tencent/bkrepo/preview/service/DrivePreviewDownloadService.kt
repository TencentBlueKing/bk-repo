package com.tencent.bkrepo.preview.service

import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.metadata.service.drive.DriveFileReadService
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.exception.PreviewNotFoundException
import org.springframework.stereotype.Component

@Component
class DrivePreviewDownloadService(
    private val driveFileReadService: DriveFileReadService,
    private val storageService: StorageService,
) {

    fun loadArtifactInputStream(
        projectId: String,
        repoName: String,
        fullPath: String,
        storageCredentials: StorageCredentials?,
    ): ArtifactInputStream {
        val blockInfo = driveFileReadService.getFileBlockInfo(projectId, repoName, fullPath)
            ?: throw PreviewNotFoundException(
                PreviewMessageCode.PREVIEW_FILE_NOT_FOUND,
                "$projectId|$repoName|$fullPath",
            )
        val range = Range.full(blockInfo.size)
        return storageService.load(blockInfo.blocks, range, storageCredentials)
            ?: throw PreviewNotFoundException(
                PreviewMessageCode.PREVIEW_FILE_NOT_FOUND,
                "$projectId|$repoName|$fullPath",
            )
    }
}
