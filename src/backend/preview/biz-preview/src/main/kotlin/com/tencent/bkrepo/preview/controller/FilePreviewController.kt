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

import com.tencent.bkrepo.common.api.exception.ParameterInvalidException
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.preview.artifact.PreviewArtifactInfo
import com.tencent.bkrepo.preview.artifact.PreviewArtifactInfo.Companion.PREVIEW_COMMON_MAPPING_URI
import com.tencent.bkrepo.preview.artifact.PreviewArtifactInfo.Companion.PREVIEW_REMOTE_MAPPING_URI
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.exception.PreviewInvalidException
import com.tencent.bkrepo.preview.service.FileHandlerService
import com.tencent.bkrepo.preview.service.FilePreviewFactory
import com.tencent.bkrepo.preview.utils.WebUtils
import com.tencent.bkrepo.preview.pojo.PreviewInfo
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

/**
 * 预览接口
 */
@RestController
class FilePreviewController(
    private val fileHandlerService: FileHandlerService,
    private val previewFactory: FilePreviewFactory
) {

    /**
     * bkrepo文件
     */
    @GetMapping(PREVIEW_COMMON_MAPPING_URI)
    fun onlinePreview(
        @ArtifactPathVariable artifactInfo: PreviewArtifactInfo,
        @RequestParam(name = "extraParam", required = false) extraParams: String?,
        req: HttpServletRequest
    ): Response<PreviewInfo> {
        val decodedParams: String?
        try {
            decodedParams = if (!extraParams.isNullOrEmpty()) {
                WebUtils.decodeUrl(extraParams!!).toString()
            } else {
                null
            }
        } catch (ex: Exception) {
            throw PreviewInvalidException(PreviewMessageCode.PREVIEW_PARAMETER_INVALID, "extraParam")
        }
        val fileAttribute = fileHandlerService.getFileAttribute(artifactInfo, decodedParams, req)
        val filePreview = previewFactory.get(fileAttribute)
        logger.info("preview file, projectId：{},repoName:{},artifactUri:{}, previewType：{}",
            artifactInfo.projectId,
            artifactInfo.repoName,
            artifactInfo.getArtifactFullPath(),
            fileAttribute.type
        )
        val previewInfo = PreviewInfo().apply {
            fileName = fileAttribute.fileName
        }

        filePreview.filePreviewHandle(fileAttribute, previewInfo)
        return ResponseBuilder.success(previewInfo)
    }

    /**
     * 远程文件
     */
    @GetMapping(PREVIEW_REMOTE_MAPPING_URI)
    fun onlinePreview(
        @RequestParam("extraParam") extraParams: String,
        req: HttpServletRequest
    ): Response<PreviewInfo> {
        val decodedParams: String
        try {
            decodedParams = WebUtils.decodeUrl(extraParams)!!
        } catch (ex: Exception) {
            throw PreviewInvalidException(PreviewMessageCode.PREVIEW_PARAMETER_INVALID, "extraParam")
        }

        val fileAttribute = fileHandlerService.getFileAttribute(decodedParams, req)
        val filePreview = previewFactory.get(fileAttribute)

        logger.info("preview file, url：{}, previewType：{}", fileAttribute.url, fileAttribute.type)

        val previewInfo = PreviewInfo().apply {
            fileName = fileAttribute.fileName
        }

        filePreview.filePreviewHandle(fileAttribute, previewInfo)
        return ResponseBuilder.success(previewInfo)
    }


    companion object {
        private val logger = LoggerFactory.getLogger(FilePreviewController::class.java)
    }
}