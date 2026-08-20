package com.tencent.bkrepo.preview.service

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.util.http.HttpRangeUtils
import com.tencent.bkrepo.common.metadata.service.drive.DriveFileReadService
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.preview.constant.PREVIEW_ARTIFACT_TO_FILE
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
        range: Range? = null,
    ): ArtifactInputStream {
        val blockInfo = driveFileReadService.getFileBlockInfo(projectId, repoName, fullPath)
            ?: throw PreviewNotFoundException(
                PreviewMessageCode.PREVIEW_FILE_NOT_FOUND,
                "$projectId|$repoName|$fullPath",
            )
        val resolvedRange = range ?: resolvePreviewRange(blockInfo.size)
        return storageService.load(blockInfo.blocks, resolvedRange, storageCredentials)
            ?: throw PreviewNotFoundException(
                PreviewMessageCode.PREVIEW_FILE_NOT_FOUND,
                "$projectId|$repoName|$fullPath",
            )
    }

    private fun resolvePreviewRange(total: Long): Range {
        val request = HttpContextHolder.getRequestOrNull() ?: return Range.full(total)
        if (request.getAttribute(PREVIEW_ARTIFACT_TO_FILE) as? Boolean == true) {
            return Range.full(total)
        }
        return try {
            HttpRangeUtils.resolveRange(request, total)
        } catch (_: IllegalArgumentException) {
            throw ErrorCodeException(
                status = HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                messageCode = CommonMessageCode.REQUEST_RANGE_INVALID,
            )
        }
    }
}
