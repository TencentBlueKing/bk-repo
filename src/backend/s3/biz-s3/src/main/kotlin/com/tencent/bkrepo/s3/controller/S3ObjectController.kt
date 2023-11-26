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

package com.tencent.bkrepo.s3.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.S3ErrorTypes
import com.tencent.bkrepo.common.api.exception.S3NotFoundException
import com.tencent.bkrepo.common.security.manager.PermissionManager
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.s3.artifact.S3ObjectArtifactInfo
import com.tencent.bkrepo.s3.artifact.S3ObjectArtifactInfo.Companion.GENERIC_MAPPING_URI
import com.tencent.bkrepo.s3.constant.S3MessageCode
import com.tencent.bkrepo.s3.service.S3ObjectService
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.net.URLDecoder

@RestController
class S3ObjectController(
    private val s3ObjectService: S3ObjectService,
    private val permissionManager: PermissionManager
) {
    @GetMapping(GENERIC_MAPPING_URI)
    fun getObject(@PathVariable bucketName: String){
        val decodedBucketName = URLDecoder.decode(bucketName, "UTF-8")
        val fullPath = URLDecoder.decode(HttpContextHolder.getRequest().requestURI, "UTF-8")
        val objectKey = extractExtraPath(fullPath, decodedBucketName)
        logger.debug("Get Object,bucket=$decodedBucketName, objectKey=$objectKey")
        // bucket命名规则： projectId.repoName
        val parts = decodedBucketName.split(".")
        if (parts.size == 2 && StringUtils.isNotEmpty(objectKey)) {
            var projectId = parts[0]
            var repoName = parts[1]
            var artifactInfo = S3ObjectArtifactInfo(projectId, repoName, objectKey)
            permissionManager.checkNodePermission(
                action = PermissionAction.READ,
                projectId = projectId,
                repoName = repoName,
                path = *arrayOf(artifactInfo.getArtifactFullPath())
            )
            s3ObjectService.getObject(artifactInfo)
        } else {
            // bucket错误或没有key，返回不存在
            logger.warn("Illegal bucket[$decodedBucketName] or key[$objectKey]")
            throw S3NotFoundException(
                HttpStatus.NOT_FOUND,
                S3MessageCode.S3_NO_SUCH_BUCKET,
                arrayOf(decodedBucketName, S3ErrorTypes.NO_SUCH_BUCKET)
            )
        }

    }

    /**
     * 提取路径中的objectKey
     */
    private fun extractExtraPath(fullPath: String, bucketName: String): String {
        val basePath = "/$bucketName/"
        val startIndex = fullPath.indexOf(basePath) + basePath.length
        return fullPath.substring(startIndex)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(S3ObjectController::class.java)
    }

}
