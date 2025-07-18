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

package com.tencent.bkrepo.preview.utils

import io.mola.galimatias.GalimatiasParseException
import jakarta.servlet.ServletRequest
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.util.HtmlUtils
import java.io.UnsupportedEncodingException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.regex.Pattern

object WebUtils {
    private val logger = LoggerFactory.getLogger(WebUtils::class.java)

    /**
     * 获取标准的URL
     *
     * @param urlStr url
     * @return 标准的URL
     */
    @Throws(GalimatiasParseException::class, MalformedURLException::class)
    fun normalizedURL(urlStr: String?): URL {
        return io.mola.galimatias.URL.parse(urlStr).toJavaURL()
    }

    /**
     * 对文件名进行编码
     *
     */
    fun encodeFileName(name: String?): String? {
        var name = name
        name = try {
            URLEncoder.encode(name, "UTF-8").replace("\\+".toRegex(), "%20")
        } catch (e: UnsupportedEncodingException) {
            return null
        }
        return name
    }

    /**
     * 去除fullfilename参数
     *
     * @param urlStr
     * @return
     */
    fun clearFullfilenameParam(urlStr: String?): String {
        // 去除特定参数字段
        val pattern = Pattern.compile("(&fullfilename=[^&]*)")
        val matcher = pattern.matcher(urlStr)
        return matcher.replaceAll("")
    }

    /**
     * 对URL进行编码
     */
    fun urlEncoderencode(urlStr: String): String {
        var urlStr = urlStr
        var fullFileName = getUrlParameterReg(urlStr, "fullfilename") //获取流文件名
        if (StringUtils.hasText(fullFileName)) {
            // 移除fullfilename参数
            urlStr = clearFullfilenameParam(urlStr)
        } else {
            fullFileName = getFileNameFromURL(urlStr) //获取文件名
        }
        if (!fullFileName?.let { UrlEncoderUtils.hasUrlEncoded(it) }!!) {  //判断文件名是否转义
            try {
                urlStr =
                    URLEncoder.encode(urlStr, "UTF-8").replace("\\+".toRegex(), "%20")
                        .replace("%3A".toRegex(), ":")
                        .replace("%2F".toRegex(), "/")
                        .replace("%3F".toRegex(), "?")
                        .replace("%26".toRegex(), "&")
                        .replace("%3D".toRegex(), "=")
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }
        }
        return urlStr
    }

    /**
     * 获取url中的参数
     *
     * @param url  url
     * @param name 参数名
     * @return 参数值
     */
    fun getUrlParameterReg(url: String, name: String): String? {
        val mapRequest: MutableMap<String, String> = HashMap()
        val strUrlParam = truncateUrlPage(url) ?: return ""
        //每个键值为一组
        val arrSplit = strUrlParam.split("[&]".toRegex()).toTypedArray()
        for (strSplit in arrSplit) {
            val arrSplitEqual = strSplit.split("[=]".toRegex()).toTypedArray()
            //解析出键值
            if (arrSplitEqual.size > 1) {
                //正确解析
                mapRequest[arrSplitEqual[0]] = arrSplitEqual[1]
            } else if (arrSplitEqual[0] != "") {
                //只有参数没有值，不加入
                mapRequest[arrSplitEqual[0]] = ""
            }
        }
        return mapRequest[name]
    }

    /**
     * 去掉url中的路径，留下请求参数部分
     *
     * @param strURL url地址
     * @return url请求参数部分
     */
    private fun truncateUrlPage(strURL: String): String? {
        var strURL = strURL
        var strAllParam: String? = null
        strURL = strURL.trim { it <= ' ' }
        val arrSplit: Array<String?> = strURL.split("[?]".toRegex()).toTypedArray()
        if (strURL.length > 1) {
            if (arrSplit.size > 1) {
                if (arrSplit[1] != null) {
                    strAllParam = arrSplit[1]
                }
            }
        }
        return strAllParam
    }

    /**
     * 从url中剥离出文件名
     *
     * @param url
     * @return 文件名
     */
    fun getFileNameFromURL(url: String): String {
        var url = url
        if (url.toLowerCase().startsWith("file:")) {
            try {
                val urlObj = URL(url)
                url = urlObj.path.substring(1)
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            }
        }
        // 因为url的参数中可能会存在/的情况，所以直接url.lastIndexOf("/")会有问题
        // 所以先从？处将url截断，然后运用url.lastIndexOf("/")获取文件名
        val noQueryUrl = url.substring(0, if (url.contains("?")) url.indexOf("?") else url.length)
        return noQueryUrl.substring(noQueryUrl.lastIndexOf("/") + 1)
    }

    /**
     * 从url中剥离出文件名
     * @param file 文件
     * @return 文件名
     */
    fun getFileNameFromMultipartFile(file: MultipartFile): String? {
        var fileName = file.originalFilename!!
        fileName = HtmlUtils.htmlEscape(fileName, FileUtils.DEFAULT_FILE_ENCODING)

        // Check for Unix-style path
        val unixSep = fileName.lastIndexOf('/')
        // Check for Windows-style path
        val winSep = fileName.lastIndexOf('\\')
        // Cut off at latest possible point
        val pos = Math.max(winSep, unixSep)
        if (pos != -1) {
            fileName = fileName.substring(pos + 1)
        }
        return fileName
    }

    /**
     * 从url中获取文件后缀
     *
     * @param url url
     * @return 文件后缀
     */
    fun suffixFromUrl(url: String): String {
        val nonPramStr = url.substring(0, if (url.contains("?")) url.indexOf("?") else url.length)
        val fileName = nonPramStr.substring(nonPramStr.lastIndexOf("/") + 1)
        return FileUtils.suffixFromFileName(fileName)
    }

    /**
     * 对url中的文件名进行UTF-8编码
     *
     * @param url url
     * @return 文件名编码后的url
     */
    fun encodeUrlFileName(url: String): String? {
        val encodedFileName: String
        val noQueryUrl = url.substring(0, if (url.contains("?")) url.indexOf("?") else url.length)
        val fileNameStartIndex = noQueryUrl.lastIndexOf('/') + 1
        val fileNameEndIndex = noQueryUrl.lastIndexOf('.')
        if (fileNameEndIndex < fileNameStartIndex) {
            return url
        }
        encodedFileName = try {
            URLEncoder.encode(noQueryUrl.substring(fileNameStartIndex, fileNameEndIndex), "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            return null
        }
        return url.substring(0, fileNameStartIndex) + encodedFileName + url.substring(fileNameEndIndex)
    }

    /**
     * 从 ServletRequest 获取预览的源 url , 已 base64 解码
     *
     * @param request 请求 request
     * @return url
     */
    fun getSourceUrl(request: ServletRequest): String? {
        val url = request.getParameter("url")
        var urls = request.getParameter("urls")
        val currentUrl = request.getParameter("currentUrl")
        val urlPath = request.getParameter("urlPath")
        if (org.apache.commons.lang3.StringUtils.isNotBlank(url)) {
            return decodeUrl(url)
        }
        if (org.apache.commons.lang3.StringUtils.isNotBlank(currentUrl)) {
            return decodeUrl(currentUrl)
        }
        if (org.apache.commons.lang3.StringUtils.isNotBlank(urlPath)) {
            return decodeUrl(urlPath)
        }
        if (org.apache.commons.lang3.StringUtils.isNotBlank(urls)) {
            urls = decodeUrl(urls)
            val images = urls!!.split("\\|".toRegex()).toTypedArray()
            return images[0]
        }
        return null
    }

    /**
     * 判断地址是否正确
     * 高 2022/12/17
     */
    fun isValidUrl(url: String?): Boolean {
        val regStr = "^((https|http|ftp|rtsp|mms|file)://)" //[.?*]表示匹配的就是本身
        val pattern = Pattern.compile(regStr)
        val matcher = pattern.matcher(url)
        return matcher.find()
    }

    /**
     * 将 Base64 字符串解码，再解码URL参数, 默认使用 UTF-8
     * @param source 原始 Base64 字符串
     * @return decoded string
     *
     */
    fun decodeUrl(source: String): String? {
        val url = decodeBase64String(source, StandardCharsets.UTF_8)
        return if (!org.apache.commons.lang3.StringUtils.isNotBlank(url)) {
            null
        } else url
    }

    /**
     * 将 Base64 字符串使用指定字符集解码
     * @param source 原始 Base64 字符串
     * @param charsets 字符集
     * @return decoded string
     */
    fun decodeBase64String(source: String?, charsets: Charset?): String? {
        /*
         * url 传入的参数里加号会被替换成空格，导致解析出错，这里需要把空格替换回加号
         * 有些 Base64 实现可能每 76 个字符插入换行符，也一并去掉
         */
        return String(
            Base64.getDecoder().decode(
                source!!.replace(" ".toRegex(), "+")
                    .replace("\n".toRegex(), "")
            ),
            charsets!!
        )
    }

    /**
     * 获取 url 的 host
     * @param urlStr url
     * @return host
     */
    fun getHost(urlStr: String?): String? {
        try {
            val url = URL(urlStr)
            return url.host.toLowerCase()
        } catch (ignored: MalformedURLException) {
        }
        return null
    }

    /**
     * 获取 session 中的 String 属性
     * @param request 请求
     * @return 属性值
     */
    fun getSessionAttr(request: HttpServletRequest, key: String?): String? {
        val session = request.session ?: return null
        val value = session.getAttribute(key) ?: return null
        return value.toString()
    }

    /**
     * 获取 session 中的 long 属性
     * @param request 请求
     * @param key 属性名
     * @return 属性值
     */
    fun getLongSessionAttr(request: HttpServletRequest, key: String?): Long {
        val value = getSessionAttr(request, key) ?: return 0
        return value.toLong()
    }

    /**
     * session 中设置属性
     * @param request 请求
     * @param key 属性名
     */
    fun setSessionAttr(request: HttpServletRequest, key: String?, value: Any?) {
        val session = request.session ?: return
        session.setAttribute(key, value)
    }

    /**
     * 移除 session 中的属性
     * @param request 请求
     * @param key 属性名
     */
    fun removeSessionAttr(request: HttpServletRequest, key: String?) {
        val session = request.session ?: return
        session.removeAttribute(key)
    }
}
