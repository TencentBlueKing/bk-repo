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

package com.tencent.bkrepo.preview.pojo

/**
 * Content :文件类型，文本，office，压缩包等等
 */
enum class FileType(val instanceName: String) {
    PICTURE("pictureFilePreviewImpl"),
    COMPRESS("compressFilePreviewImpl"),
    OFFICE("officeFilePreviewImpl"),
    SIMTEXT("simTextFilePreviewImpl"),
    PDF("pdfFilePreviewImpl"),
    CODE("codeFilePreviewImpl"),
    OTHER("otherFilePreviewImpl"),
    MEDIA("mediaFilePreviewImpl"),
    MEDIACONVERT("mediaFilePreviewImpl"),
    MARKDOWN("markdownFilePreviewImpl"),
    XML("xmlFilePreviewImpl"),
    CAD("cadFilePreviewImpl"),
    TIFF("tiffFilePreviewImpl"),
    OFD("ofdFilePreviewImpl"),
    EML("emlFilePreviewImpl"),
    ONLINE3D("online3DFilePreviewImpl"),
    XMIND("xmindFilePreviewImpl"),
    SVG("svgFilePreviewImpl"),
    EPUB("epubFilePreviewImpl"),
    BPMN("bpmnFilePreviewImpl"),
    DCM("dcmFilePreviewImpl"),
    DRAWIO("drawioFilePreviewImpl");

    companion object {
        private val OFFICE_TYPES = arrayOf(
            "docx",
            "wps",
            "doc",
            "docm",
            "xls",
            "xlsx",
            "csv",
            "xlsm",
            "ppt",
            "pptx",
            "vsd",
            "rtf",
            "odt",
            "wmf",
            "emf",
            "dps",
            "et",
            "ods",
            "ots",
            "tsv",
            "odp",
            "otp",
            "sxi",
            "ott",
            "vsdx",
            "fodt",
            "fods",
            "xltx",
            "tga",
            "psd",
            "dotm",
            "ett",
            "xlt",
            "xltm",
            "wpt",
            "dot",
            "xlam",
            "dotx",
            "xla",
            "pages",
            "eps"
        )
        private val PICTURE_TYPES = arrayOf("jpg", "jpeg", "png", "gif", "bmp", "ico", "jfif", "webp")
        private val ARCHIVE_TYPES = arrayOf("rar", "zip", "jar", "7-zip", "tar", "gzip", "7z")
        private val ONLINE3D_TYPES = arrayOf(
            "obj",
            "3ds",
            "stl",
            "ply",
            "off",
            "3dm",
            "fbx",
            "dae",
            "wrl",
            "3mf",
            "ifc",
            "glb",
            "o3dv",
            "gltf",
            "stp",
            "bim",
            "fcstd",
            "step",
            "iges",
            "brep"
        )
        private val EML_TYPES = arrayOf("eml")
        private val XMIND_TYPES = arrayOf("xmind")
        private val EPUB_TYPES = arrayOf("epub")
        private val DCM_TYPES = arrayOf("dcm")
        private val DRAWIO_TYPES = arrayOf("drawio")
        private val XML_TYPES = arrayOf("xml", "xbrl")
        private val TIFF_TYPES = arrayOf("tif", "tiff")
        private val OFD_TYPES = arrayOf("ofd")
        private val SVG_TYPES = arrayOf("svg")
        private val CAD_TYPES =
            arrayOf("dwg", "dxf", "dwf", "iges", "igs", "dwt", "dng", "ifc", "dwfx", "stl", "cf2", "plt")
        private val CODES = arrayOf(
            "java",
            "c",
            "php",
            "go",
            "python",
            "py",
            "js",
            "html",
            "ftl",
            "css",
            "lua",
            "sh",
            "rb",
            "yaml",
            "yml",
            "json",
            "h",
            "cpp",
            "cs",
            "aspx",
            "jsp",
            "sql"
        )

        private val FILE_TYPE_MAPPER = mutableMapOf<String, FileType>()

        init {
            OFFICE_TYPES.forEach { fileType -> FILE_TYPE_MAPPER[fileType] = OFFICE }
            PICTURE_TYPES.forEach { fileType -> FILE_TYPE_MAPPER[fileType] = PICTURE }
            ARCHIVE_TYPES.forEach { fileType -> FILE_TYPE_MAPPER[fileType] = COMPRESS }
            TIFF_TYPES.forEach { fileType -> FILE_TYPE_MAPPER[fileType] = TIFF }
            CODES.forEach { fileType -> FILE_TYPE_MAPPER[fileType] = CODE }
            OFD_TYPES.forEach { fileType -> FILE_TYPE_MAPPER[fileType] = OFD }
            CAD_TYPES.forEach { fileType -> FILE_TYPE_MAPPER[fileType] = CAD }
            SVG_TYPES.forEach { fileType -> FILE_TYPE_MAPPER[fileType] = SVG }
            EPUB_TYPES.forEach { fileType -> FILE_TYPE_MAPPER[fileType] = EPUB }
            EML_TYPES.forEach { fileType -> FILE_TYPE_MAPPER[fileType] = EML }
            XMIND_TYPES.forEach { fileType -> FILE_TYPE_MAPPER[fileType] = XMIND }
            ONLINE3D_TYPES.forEach { fileType -> FILE_TYPE_MAPPER[fileType] = ONLINE3D }
            DCM_TYPES.forEach { fileType -> FILE_TYPE_MAPPER[fileType] = DCM }
            DRAWIO_TYPES.forEach { fileType -> FILE_TYPE_MAPPER[fileType] = DRAWIO }
            XML_TYPES.forEach { fileType -> FILE_TYPE_MAPPER[fileType] = XML }
            FILE_TYPE_MAPPER["md"] = MARKDOWN
            FILE_TYPE_MAPPER["pdf"] = PDF
            FILE_TYPE_MAPPER["bpmn"] = BPMN
        }

        private fun to(fileType: String): FileType {
            return FILE_TYPE_MAPPER[fileType] ?: OTHER
        }

        /**
         * 查看文件类型(防止参数中存在.点号或者其他特殊字符，所以先抽取文件名，然后再获取文件类型)
         *
         * @param url url
         * @return 文件类型
         */
        fun typeFromUrl(url: String): FileType {
            val nonPramStr = url.substring(0, url.indexOf("?").takeIf { it != -1 } ?: url.length)
            val fileName = nonPramStr.substring(nonPramStr.lastIndexOf("/") + 1)
            return typeFromFileName(fileName)
        }

        fun typeFromFileName(fileName: String): FileType {
            val fileType = fileName.substring(fileName.lastIndexOf(".") + 1)
            val lowerCaseFileType = fileType.lowercase()
            return to(lowerCaseFileType)
        }
    }
}
