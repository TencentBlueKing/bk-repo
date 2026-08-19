package com.tencent.bkrepo.preview.service.impl

import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.exception.PreviewSystemException
import com.tencent.bkrepo.preview.pojo.FileAttribute
import com.tencent.bkrepo.preview.service.FilePreview
import com.tencent.bkrepo.preview.service.FileTransferService
import org.springframework.stereotype.Service

/**
 * 媒体文件：按 Range 代理源制品，不转换、不落地整文件。
 */
@Service
class MediaFilePreviewImpl(
    private val fileTransferService: FileTransferService
) : FilePreview {
    override fun filePreviewHandle(fileAttribute: FileAttribute) {
        if (fileAttribute.storageType != 0) {
            throw PreviewSystemException(
                PreviewMessageCode.PREVIEW_FILE_NOT_SUPPORT_ERROR,
                fileAttribute.suffix.orEmpty()
            )
        }
        fileTransferService.sendOriginalFileAsResponse(fileAttribute)
    }
}
