/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.preview.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.preview.artifact.PreviewArtifactInfo
import com.tencent.bkrepo.preview.artifact.PreviewArtifactInfo.Companion.PREVIEW_BKREPO_MAPPING_URI
import com.tencent.bkrepo.preview.artifact.PreviewArtifactInfo.Companion.PREVIEW_INFO_BKREPO_MAPPING_URI
import com.tencent.bkrepo.preview.artifact.PreviewArtifactInfo.Companion.PREVIEW_INFO_REMOTE_MAPPING_URI
import com.tencent.bkrepo.preview.artifact.PreviewArtifactInfo.Companion.PREVIEW_REMOTE_MAPPING_URI
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.exception.PreviewInvalidException
import com.tencent.bkrepo.preview.pojo.PreviewInfo
import com.tencent.bkrepo.preview.service.CommonResourceService
import com.tencent.bkrepo.preview.service.FileHandlerService
import com.tencent.bkrepo.preview.service.FilePreviewFactory
import com.tencent.bkrepo.preview.utils.WebUtils
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 预览接口
 */
@RestController
class FilePreviewController(
    private val fileHandlerService: FileHandlerService,
    private val previewFactory: FilePreviewFactory,
    private val resourceService: CommonResourceService
) {

    /**
     * 远程文件预览属性
     */
    @GetMapping(PREVIEW_INFO_REMOTE_MAPPING_URI)
    @CrossOrigin
    fun getPreviewInfo(
        @RequestParam("extraParam") extraParams: String
    ): Response<PreviewInfo> {
        val decodedParams = decodeParams(extraParams)
        val previewInfo = fileHandlerService.buildFilePreviewInfo(decodedParams!!)
        return ResponseBuilder.success(previewInfo)
    }

    /**
     * bkrepo文件预览属性
     */
    @GetMapping(PREVIEW_INFO_BKREPO_MAPPING_URI)
    @Permission(ResourceType.NODE, PermissionAction.READ)
    @CrossOrigin
    fun getPreviewInfo(
        @ArtifactPathVariable artifactInfo: PreviewArtifactInfo,
        @RequestParam(name = "extraParam", required = false) extraParam: String?,
    ): Response<PreviewInfo> {
        val decodedParams = decodeParams(extraParam)
        val previewInfo = fileHandlerService.buildFilePreviewInfo(artifactInfo, decodedParams)
        return ResponseBuilder.success(previewInfo)
    }

    /**
     * bkrepo文件
     */
    @GetMapping(PREVIEW_BKREPO_MAPPING_URI)
    @Permission(ResourceType.NODE, PermissionAction.DOWNLOAD)
    @CrossOrigin
    fun onlinePreview(
        @ArtifactPathVariable artifactInfo: PreviewArtifactInfo,
        @RequestParam(name = "extraParam", required = false) extraParam: String?
    ) {
        val decodedParams = decodeParams(extraParam)
        val fileAttribute = fileHandlerService.getFileAttribute(artifactInfo, decodedParams)
        val filePreview = previewFactory.get(fileAttribute)
        logger.info("preview file from bkrepo, projectId：{},repoName:{},artifactUri:{}, previewType：{}",
            artifactInfo.projectId,
            artifactInfo.repoName,
            artifactInfo.getArtifactFullPath(),
            fileAttribute.type
        )
        filePreview.filePreviewHandle(fileAttribute)
    }

    /**
     * 远程文件
     */
    @GetMapping(PREVIEW_REMOTE_MAPPING_URI)
    @CrossOrigin
    fun onlinePreview(
        @RequestParam("extraParam") extraParam: String
    ) {
        val decodedParams = decodeParams(extraParam)
        val fileAttribute = fileHandlerService.getFileAttribute(decodedParams!!)
        val filePreview = previewFactory.get(fileAttribute)
        logger.info("preview file from remote, url：{}, previewType：{}", fileAttribute.url, fileAttribute.type)
        filePreview.filePreviewHandle(fileAttribute)
    }

    private fun decodeParams(extraParam: String?): String? {
        val decodedParams: String?
        try {
            decodedParams = if (!extraParam.isNullOrEmpty()) {
                WebUtils.decodeUrl(extraParam!!).toString()
            } else {
                null
            }
        } catch (ex: Exception) {
            logger.error("Decryption parameter [extraParams] failed", ex)
            throw PreviewInvalidException(PreviewMessageCode.PREVIEW_PARAMETER_INVALID, "extraParam")
        }
        return decodedParams
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FilePreviewController::class.java)
    }
}