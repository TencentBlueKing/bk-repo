package com.tencent.bkrepo.preview.service

import com.tencent.bkrepo.preview.pojo.FileAttribute
import com.tencent.bkrepo.preview.pojo.PreviewInfo

interface FilePreview {
    fun filePreviewHandle(fileAttribute: FileAttribute, previewInfo: PreviewInfo): PreviewInfo?

    companion object {
        const val PDF_FILE_PREVIEW_PAGE = "pdf"
        const val PPT_FILE_PREVIEW_PAGE = "ppt"
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