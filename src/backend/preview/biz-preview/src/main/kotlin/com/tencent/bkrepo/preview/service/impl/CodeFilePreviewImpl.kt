package com.tencent.bkrepo.preview.service.impl

import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.preview.config.configuration.PreviewConfig
import com.tencent.bkrepo.preview.pojo.FileAttribute
import com.tencent.bkrepo.preview.service.FileTransferService
import com.tencent.bkrepo.preview.service.cache.impl.PreviewFileCacheServiceImpl
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException

/**
 * 代码预览
 */
@Service
class CodeFilePreviewImpl(
    private val config: PreviewConfig,
    private val fileTransferService: FileTransferService,
    private val previewFileCacheService: PreviewFileCacheServiceImpl,
    private val nodeService: NodeService
) : AbstractFilePreview(
    config,
    fileTransferService,
    previewFileCacheService,
    nodeService
) {
    override fun processFileContent(fileAttribute: FileAttribute) {
        try {
            correctAndBase64EncodeFile(fileAttribute.finalFilePath!!, fileAttribute.fileName!!)
        } catch (e: IOException) {
            logger.error("The text was an error in encoding", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CodeFilePreviewImpl::class.java)
    }
}