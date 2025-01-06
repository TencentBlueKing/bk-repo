package com.tencent.bkrepo.preview.service

import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.preview.config.configuration.PreviewConfig
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.exception.PreviewInvalidException
import com.tencent.bkrepo.preview.pojo.FileAttribute
import com.tencent.bkrepo.preview.pojo.FileType
import com.tencent.bkrepo.preview.pojo.PreviewInfo
import com.tencent.bkrepo.preview.utils.FileUtils
import com.tencent.bkrepo.preview.utils.UrlEncoderUtils
import com.tencent.bkrepo.preview.utils.WebUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.UUID

@Component
class FileHandlerService(
    private val config: PreviewConfig,
    private val resourceService: CommonResourceService
) {

    companion object {
        private const val URI_ENCODING = "UTF-8"
        private val logger = LoggerFactory.getLogger(FileHandlerService::class.java)
    }

    /**
     * 预览信息
     */
    fun buildFilePreviewInfo(decodedParams: String): PreviewInfo {
        val fileAttribute = getFileAttribute(decodedParams)
        val fileTemplate = getFileTemplate(fileAttribute)
        val watermark = resourceService.getWatermark(decodedParams)
        logger.info("Preview info, url：{}, previewType：{}, fileTemplate: {}",
            fileAttribute.url, fileAttribute.type, fileTemplate)
        return PreviewInfo(
            fileName = fileAttribute.fileName,
            fileType = fileAttribute.type!!.name,
            suffix = fileAttribute.suffix,
            fileTemplate = fileTemplate,
            watermark = watermark
        )
    }

    /**
     * 预览信息
     */
    fun buildFilePreviewInfo(artifactInfo: ArtifactInfo, decodedParams: String?): PreviewInfo {
        val fileAttribute = getFileAttribute(artifactInfo, decodedParams)
        val fileTemplate = getFileTemplate(fileAttribute)
        val watermark = resourceService.getWatermark(decodedParams)
        logger.info("Preview info, url：{}, previewType：{}, fileTemplate: {}",
            fileAttribute.url, fileAttribute.type, fileTemplate)
        return PreviewInfo(
            fileName = fileAttribute.fileName,
            fileType = fileAttribute.type!!.name,
            suffix = fileAttribute.suffix,
            fileTemplate = fileTemplate,
            watermark = watermark
        )
    }

    /**
     * 获取文件预览模板
     *
     * @param params 原始参数
     * @return 预览模板
     */
    fun getFileTemplate(fileAttribute: FileAttribute): String = with(fileAttribute) {
        when {
            isHtmlView -> FilePreview.EXEL_FILE_PREVIEW_PAGE
            type?.name == FileType.OFFICE.name -> when (suffix!!.lowercase()) {
                "xlsx" -> FilePreview.XLSX_FILE_PREVIEW_PAGE
                "csv"  -> FilePreview.CSV_FILE_PREVIEW_PAGE
                else   -> FilePreview.PDF_FILE_PREVIEW_PAGE
            }
            type?.name == FileType.PDF.name -> FilePreview.PDF_FILE_PREVIEW_PAGE
            type?.name == FileType.SIMTEXT.name -> FilePreview.TXT_FILE_PREVIEW_PAGE
            type?.name == FileType.CODE.name -> FilePreview.CODE_FILE_PREVIEW_PAGE
            type?.name == FileType.PICTURE.name -> FilePreview.PICTURE_FILE_PREVIEW_PAGE
            type?.name == FileType.XML.name -> FilePreview.XML_FILE_PREVIEW_PAGE
            type?.name == FileType.MARKDOWN.name -> FilePreview.MARKDOWN_FILE_PREVIEW_PAGE
            type?.name == FileType.XMIND.name -> FilePreview.XMIND_FILE_PREVIEW_PAGE
            type?.name == FileType.MEDIA.name -> FilePreview.MEDIA_FILE_PREVIEW_PAGE
            else -> FilePreview.NOT_SUPPORTED_FILE_PAGE
        }
    }

    /**
     * 获取文件属性
     *
     * @param params 原始参数
     * @return 文件属性
     */
    fun getFileAttribute(params: String): FileAttribute {
        val attribute = params.readJsonString<FileAttribute>()
        checkRequest(attribute)
        adjustProperties(attribute)
        return attribute
    }

    /**
     * 获取文件属性
     *
     * @param params 原始参数
     * @return 文件属性
     */
    fun getFileAttribute(artifactInfo: ArtifactInfo, params: String?): FileAttribute {
        val attribute = params?.readJsonString<FileAttribute>() ?: FileAttribute()
        attribute.projectId = artifactInfo.projectId
        attribute.repoName = artifactInfo.repoName
        attribute.artifactUri = artifactInfo.getArtifactFullPath()
        checkRequest(attribute)
        adjustProperties(attribute)
        return attribute
    }

    /**
     * 获取转换的文件名
     *
     * @return 文件名
     */
    private fun getConvertFileName(
        type: FileType,
        originFileName: String,
        convertFilePrefixName: String,
        isHtmlView: Boolean,
        suffix: String,
        isCompressFile: Boolean
    ): String {
        var convertFileName = when (type) {
            FileType.OFFICE -> {
                if (suffix.equals("csv", ignoreCase = true) || suffix.equals("xlsx", ignoreCase = true)) {
                    originFileName
                } else {
                    convertFilePrefixName + if (isHtmlView) "html" else "pdf"
                }
            }
            FileType.PDF -> originFileName
            FileType.MEDIACONVERT -> convertFilePrefixName + "mp4"
            else -> originFileName
        }
        if (isCompressFile) { // 判断是否使用特定压缩包符号
            convertFileName = "_decompression$convertFileName"
        }
        return convertFileName
    }

    /**
     * 请求校验
     */
    private fun checkRequest(fileAttribute: FileAttribute) {
        val isInvalid = listOf(
            fileAttribute.projectId,
            fileAttribute.repoName,
            fileAttribute.artifactUri,
            fileAttribute.url
        ).all { it.isNullOrEmpty() }
        if (isInvalid) {
            logger.error("The file download info cannot be empty")
            throw PreviewInvalidException(PreviewMessageCode.PREVIEW_PARAMETER_INVALID, "url")
        }
    }

    /**
     * 参数完善
     */
    private fun adjustProperties(fileAttribute: FileAttribute) {
        var suffix: String
        var type: FileType
        var originFileName: String // 原始文件名
        val fullFileName = fileAttribute.fileName
        var url = fileAttribute.url

        // 设置 storageType
        fileAttribute.storageType = determineStorageType(fileAttribute)

        // 判断文件名和类型
        if (!fullFileName.isNullOrBlank()) {
            originFileName = fullFileName
            type = FileType.typeFromFileName(fullFileName)
            suffix = FileUtils.suffixFromFileName(fullFileName)
        } else {
            if (fileAttribute.storageType == 0) {
                originFileName = WebUtils.getFileNameFromURL(fileAttribute.artifactUri!!)
                type = FileType.typeFromUrl(fileAttribute.artifactUri!!)
                suffix = WebUtils.suffixFromUrl(fileAttribute.artifactUri!!)
            } else {
                originFileName = WebUtils.getFileNameFromURL(url!!)
                type = FileType.typeFromUrl(url)
                suffix = WebUtils.suffixFromUrl(url)
            }
        }

        // 判断文件名是否转义
        if (UrlEncoderUtils.hasUrlEncoded(originFileName)) {
            try {
                originFileName = URLDecoder.decode(originFileName, URI_ENCODING)
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
                logger.error("File name [$originFileName] escaping error.", e)
            }
        } else {
            url = url?.let { WebUtils.encodeUrlFileName(it) } // 对未转义的url进行转义
        }

        // 文件名处理
        originFileName = FileUtils.htmlEscape(originFileName)

        // 转换后的文件名前缀
        var convertFilePrefixName: String? = null
        try {
            convertFilePrefixName = originFileName.substring(0, originFileName.lastIndexOf(".")) + suffix + "."
        } catch (e: Exception) {
            logger.error("Get file name suffix incorrectly：", e)
        }

        // 判断是否为 HTML 视图
        val isHtmlView = isHtmlView(suffix)

        // 获取缓存文件名
        val convertFileName = convertFilePrefixName?.let {
            getConvertFileName(type, originFileName, it, isHtmlView, suffix, false)
        }

        // 获取输出文件路径
        val outFilePath = generateOutputFilePath(convertFileName)

        // 设置 FileAttribute 属性
        fileAttribute.apply {
            this.type = type
            this.fileName = originFileName
            this.suffix = suffix
            this.url = url
            this.outFilePath = outFilePath
            this.convertFileName = convertFileName
            this.isHtmlView = isHtmlView
        }
    }

    // 生成输出文件路径
    private fun generateOutputFilePath(convertFileName: String?): String {
        return "${config.fileDir}${File.separator}convert${File.separator}" +
                "${UUID.randomUUID()}${File.separator}$convertFileName"
    }

    // 解析 storageType
    private fun determineStorageType(fileAttribute: FileAttribute): Int {
        return if (!fileAttribute.projectId.isNullOrBlank() &&
            !fileAttribute.repoName.isNullOrBlank() &&
            !fileAttribute.artifactUri.isNullOrBlank()) {
            0
        } else {
            1
        }
    }

    // 判断是否为HTML视图
    private fun isHtmlView(suffix: String): Boolean {
        val htmlSuffixes = setOf("xls", "xlsm", "xlt", "xltm", "et", "ett", "xlam")
        return htmlSuffixes.contains(suffix.lowercase())
    }
}
