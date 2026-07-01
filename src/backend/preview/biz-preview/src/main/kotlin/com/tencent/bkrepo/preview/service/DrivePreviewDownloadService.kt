/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 */

package com.tencent.bkrepo.preview.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.fs.server.api.DriveBlockListClient
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.exception.PreviewNotFoundException
import org.springframework.stereotype.Component

@Component
class DrivePreviewDownloadService(
    private val driveBlockListClient: DriveBlockListClient,
    private val storageService: StorageService,
) {

    fun loadArtifactInputStream(
        projectId: String,
        repoName: String,
        fullPath: String,
        storageCredentials: StorageCredentials?,
    ): ArtifactInputStream {
        val blockInfo = try {
            driveBlockListClient.listBlocks(projectId, repoName, fullPath).data
        } catch (e: ErrorCodeException) {
            throw PreviewNotFoundException(
                PreviewMessageCode.PREVIEW_FILE_NOT_FOUND,
                "$projectId|$repoName|$fullPath",
            )
        } ?: throw PreviewNotFoundException(
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
