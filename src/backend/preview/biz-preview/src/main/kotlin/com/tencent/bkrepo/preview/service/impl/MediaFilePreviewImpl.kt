package com.tencent.bkrepo.preview.service.impl

import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.preview.config.configuration.PreviewConfig
import com.tencent.bkrepo.preview.service.FileTransferService
import com.tencent.bkrepo.preview.service.cache.impl.PreviewFileCacheServiceImpl
import org.springframework.stereotype.Service

/**
 * media文件
 */
@Service
class MediaFilePreviewImpl(
    private val config: PreviewConfig,
    private val fileTransferService: FileTransferService,
    private val previewFileCacheService: PreviewFileCacheServiceImpl,
    private val nodeService: NodeService
) : AbstractFilePreview(
    config,
    fileTransferService,
    previewFileCacheService,
    nodeService
)