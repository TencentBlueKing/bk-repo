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

package com.tencent.bkrepo.preview.service.impl

import com.tencent.bkrepo.preview.pojo.FileAttribute
import com.tencent.bkrepo.preview.utils.FileUtils.htmlEscape
import com.tencent.bkrepo.preview.service.FilePreview
import com.tencent.bkrepo.preview.pojo.PreviewInfo
import org.springframework.stereotype.Service

@Service
class OtherFilePreviewImpl : FilePreview {
    override fun filePreviewHandle(fileAttribute: FileAttribute, previewInfo: PreviewInfo): PreviewInfo {
        return this.notSupportedFile(fileAttribute,
            previewInfo,
            "The system does not yet support online preview of files in this format"
        )
    }

    /**
     * 通用的预览失败，导向到不支持的文件响应页面
     *
     * @return 页面
     */
    fun notSupportedFile(fileAttribute: FileAttribute, previewInfo: PreviewInfo, errMsg: String?): PreviewInfo {
        return this.notSupportedFile(previewInfo, fileAttribute.suffix!!, errMsg)
    }

    /**
     * 通用的预览失败，导向到不支持的文件响应页面
     *
     * @return 页面
     */
    fun notSupportedFile(previewInfo: PreviewInfo, errMsg: String?): PreviewInfo {
        return this.notSupportedFile(previewInfo, "Unknown", errMsg)
    }

    /**
     * 通用的预览失败，导向到不支持的文件响应页面
     *
     * @return 页面
     */
    fun notSupportedFile(previewInfo: PreviewInfo, fileType: String, errMsg: String?): PreviewInfo {
        previewInfo.msg = htmlEscape(errMsg?:"")
        previewInfo.fileTemplate = FilePreview.NOT_SUPPORTED_FILE_PAGE
        previewInfo.fileType = htmlEscape(fileType)
        return previewInfo
    }
}