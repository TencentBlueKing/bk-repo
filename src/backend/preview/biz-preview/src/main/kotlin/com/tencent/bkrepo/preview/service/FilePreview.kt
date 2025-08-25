/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.preview.service

import com.tencent.bkrepo.preview.pojo.FileAttribute

interface FilePreview {

    /**
     * 预览文件处理，最终的预览文件流由response输出
     */
    fun filePreviewHandle(fileAttribute: FileAttribute)

    companion object {
        const val PDF_FILE_PREVIEW_PAGE = "pdf"
        const val COMPRESS_FILE_PREVIEW_PAGE = "compress"
        const val MEDIA_FILE_PREVIEW_PAGE = "media"
        const val PICTURE_FILE_PREVIEW_PAGE = "picture"
        const val TIFF_FILE_PREVIEW_PAGE = "tiff"
        const val OFD_FILE_PREVIEW_PAGE = "ofd"
        const val SVG_FILE_PREVIEW_PAGE = "svg"
        const val ONLINE3D_PREVIEW_PAGE = "online3D"
        const val EPUB_PREVIEW_PAGE = "epub"
        const val XMIND_FILE_PREVIEW_PAGE = "xmind"
        const val EML_FILE_PREVIEW_PAGE = "eml"
        const val OFFICE_PICTURE_FILE_PREVIEW_PAGE = "officePicture"
        const val TXT_FILE_PREVIEW_PAGE = "txt"
        const val CODE_FILE_PREVIEW_PAGE = "code"
        const val EXEL_FILE_PREVIEW_PAGE = "html"
        const val XML_FILE_PREVIEW_PAGE = "xml"
        const val MARKDOWN_FILE_PREVIEW_PAGE = "markdown"
        const val BPMN_FILE_PREVIEW_PAGE = "bpmn"
        const val DCM_FILE_PREVIEW_PAGE = "dcm"
        const val DRAWUI_FILE_PREVIEW_PAGE = "drawio"
        const val NOT_SUPPORTED_FILE_PAGE = "fileNotSupported"
        const val XLSX_FILE_PREVIEW_PAGE = "officeweb"
        const val CSV_FILE_PREVIEW_PAGE = "csv"
    }
}