/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.generic.constant

import com.tencent.bkrepo.common.api.message.MessageCode

/**
 * 通用文件错误码
 */
enum class GenericMessageCode(private val businessCode: Int, private val key: String) : MessageCode {
    UPLOAD_ID_NOT_FOUND(1, "generic.uploadId.notfound"),
    LIST_DIR_NOT_ALLOWED(2, "generic.dir.not-allowed"),
    SIGN_FILE_NOT_FOUND(3, "generic.delta.sign-file.notfound"),
    NODE_DATA_ERROR(4, "generic.node.data.error"),
    DOWNLOAD_DIR_NOT_ALLOWED(5, "generic.download.dir.not-allowed"),
    ARTIFACT_SEARCH_FAILED(6, "generic.artifact.query.failed"),
    PIPELINE_ARTIFACT_OVERWRITE_NOT_ALLOWED(7, "generic.pipeline-artifact.overwrite.not-allowed"),
    CUSTOM_ARTIFACT_OVERWRITE_NOT_ALLOWED(8, "generic.custom-artifact.overwrite.not-allowed"),
    PIPELINE_METADATA_INCOMPLETE(9, "generic.pipeline.metadata.incomplete"),
    PIPELINE_REPO_MANUAL_UPLOAD_NOT_ALLOWED(10, "generic.pipeline-repo.manual-upload.not-allowed"),
    PIPELINE_ARTIFACT_PATH_ILLEGAL(11, "generic.pipeline.artifact.path.illegal"),
    CHUNKED_ARTIFACT_BROKEN(12, "generic.chunked.artifact.broken"),
    BLOCK_UPLOADID_ERROR(13, "generic.block.uploadId.error"),
    BLOCK_HEAD_NOT_FOUND(14, "generic.block.node.head.not-found"),
    BLOCK_UPDATE_LIST_IS_NULL(15, "generic.block.update.list.is.null"),
    USER_SHARE_CONFIG_NOT_FOUND(16, "generic.user.share.config.notfound"),
    APPROVAL_NOT_FOUND(17, "generic.approval.notfound"),
    USER_SHARE_NOT_FOUND(18, "generic.user.share.notfound"),
    USER_SHARE_NO_PERMITS(19, "generic.user.share.no-permits"),
    USER_SHARE_EXPIRED(20, "generic.user.share.expired"),
    ;

    override fun getBusinessCode() = businessCode
    override fun getKey() = key
    override fun getModuleCode() = 12
}
