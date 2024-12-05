package com.tencent.bkrepo.preview.service

import com.tencent.bkrepo.common.api.exception.ParameterInvalidException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.preview.config.configuration.PreviewConfig
import com.tencent.bkrepo.preview.constant.PreviewMessageCode
import com.tencent.bkrepo.preview.exception.PreviewInvalidException
import com.tencent.bkrepo.preview.utils.*
import com.tencent.bkrepo.preview.pojo.FileAttribute
import com.tencent.bkrepo.preview.pojo.FileType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import java.io.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.*
import javax.servlet.http.HttpServletRequest

@Component
class FileHandlerService(private val config: PreviewConfig) {

    companion object {
        private const val URI_ENCODING = "UTF-8"
        private const val PDF2JPG_IMAGE_FORMAT = ".jpg"
        private val logger = LoggerFactory.getLogger(FileHandlerService::class.java)
    }

    /**
     * 对转换后的文件进行操作(改变编码方式)
     *
     * @param outFilePath 文件绝对路径
     */
    fun doActionConvertedFile(outFilePath: String) {
        val charset = EncodingDetects.getJavaEncode(outFilePath) // 假设返回的是 String?
        val sb = StringBuilder()
        try {
            FileInputStream(outFilePath).bufferedReader(charset = Charset.forName(charset)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.contains("charset=gb2312")) {
                        line = line!!.replace("charset=gb2312", "charset=utf-8")
                    }
                    sb.append(line)
                }
                // 添加sheet控制头
                sb.append("<script src=\"js/jquery-3.6.1.min.js\" type=\"text/javascript\"></script>")
                sb.append("<script src=\"excel/excel.header.js\" type=\"text/javascript\"></script>")
                sb.append("<link rel=\"stylesheet\" href=\"excel/excel.css\">")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        // 重新写入文件
        try {
            FileOutputStream(outFilePath).bufferedWriter(Charset.forName("UTF-8")).use { writer ->
                writer.write(sb.toString())
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * 获取本地 pdf 转 image 后的 web 访问地址
     *
     * @param pdfFilePath pdf文件名
     * @param index 图片索引
     * @return 图片访问地址
     */
    private fun getPdf2jpgUrl(pdfFilePath: String, index: Int): String {
        val baseUrl = "" // 设置 baseUrl
        val pdfFolder = pdfFilePath.replace(config.fileDir, "").removeSuffix(".pdf")
        val urlPrefix: String
        urlPrefix = try {
            "$baseUrl${URLEncoder.encode(pdfFolder, URI_ENCODING).replace("+", "%20")}"
        } catch (e: UnsupportedEncodingException) {
            logger.error("UnsupportedEncodingException", e)
            "$baseUrl$pdfFolder"
        }
        return "$urlPrefix/$index$PDF2JPG_IMAGE_FORMAT"
    }

    /**
     * @param str 原字符串（待截取原串）
     * @param posStr 指定字符串
     * @return 截取指定字符串之后的数据
     */
    fun getSubString(str: String, posStr: String): String {
        return str.substring(str.indexOf(posStr) + posStr.length)
    }

    /**
     * 获取文件属性
     *
     * @param params 原始参数
     * @return 文件属性
     */
    fun getFileAttribute(params: String, req: HttpServletRequest): FileAttribute {
        val jsonMapper = JsonMapper()
        val attribute = jsonMapper.fromJson(params, FileAttribute::class.java)?: FileAttribute()
        checkRequest(attribute, req)
        adjustProperties(attribute, req)
        return attribute
    }

    /**
     * 获取文件属性
     *
     * @param params 原始参数
     * @return 文件属性
     */
    fun getFileAttribute(artifactInfo: ArtifactInfo, params: String?, req: HttpServletRequest): FileAttribute {
        val jsonMapper = JsonMapper()
        val attribute = jsonMapper.fromJson(params, FileAttribute::class.java)?: FileAttribute()
        attribute.projectId = artifactInfo.projectId
        attribute.repoName = artifactInfo.repoName
        attribute.artifactUri = artifactInfo.getArtifactFullPath()
        checkRequest(attribute, req)
        adjustProperties(attribute, req)
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
        isCompressFile: Boolean
    ): String {
        var convertFileName = when (type) {
            FileType.OFFICE -> convertFilePrefixName + if (isHtmlView) "html" else "pdf"
            FileType.PDF -> originFileName
            FileType.MEDIACONVERT -> convertFilePrefixName + "mp4"
            else -> originFileName
        }
        if (isCompressFile) { // 判断是否使用特定压缩包符号
            convertFileName = "_decompression$convertFileName"
        }
        return convertFileName
    }

    private fun getBaseUrl(request: HttpServletRequest): String {
        // 1. 支持通过 http header 中 X-Base-Url 来动态设置 baseUrl 以支持多个域名/项目的共享使用
        val urlInHeader = request.getHeader("X-Base-Url")
        var baseUrl: String

        baseUrl = if (!urlInHeader.isNullOrEmpty()) {
            urlInHeader
        } else (if (!config.domain.isNullOrEmpty()) {
            // 2. 如果配置文件中配置了 baseUrl 且不为 default 则以配置文件为准
            config.domain
        } else {
            // 3. 默认动态拼接 baseUrl
            "${request.scheme}://${request.serverName}:${request.serverPort}${request.contextPath}/"
        }).toString()

        if (!baseUrl.endsWith("/")) {
            baseUrl = "$baseUrl/"
        }

        return baseUrl
    }



    /**
     * 请求校验
     */
    private fun checkRequest(fileAttribute: FileAttribute, req: HttpServletRequest) {
        if (fileAttribute.projectId.isNullOrEmpty()
            && fileAttribute.repoName.isNullOrEmpty()
            && fileAttribute.artifactUri.isNullOrEmpty()
            && fileAttribute.url.isNullOrEmpty()) {
            logger.error("The file download info cannot be empty")
            throw PreviewInvalidException(PreviewMessageCode.PREVIEW_PARAMETER_INVALID, "url")
        }
    }

    /**
     * 参数完善
     */
    private fun adjustProperties(fileAttribute: FileAttribute, req: HttpServletRequest) {
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
            getConvertFileName(type, originFileName, it, isHtmlView, false)
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
            this.baseUrl = getBaseUrl(req)
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

    // 判断是否为 HTML 视图
    private fun isHtmlView(suffix: String): Boolean {
        val htmlSuffixes = setOf("xls", "xlsx", "csv", "xlsm", "xlt", "xltm", "et", "ett", "xlam")
        return htmlSuffixes.contains(suffix.lowercase())
    }
}
