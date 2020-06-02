package com.tencent.bkrepo.composer.util

import com.tencent.bkrepo.composer.exception.ComposerUnSupportCompressException
import java.util.regex.Pattern

object UriUtil {
    /**
     * 根据uri解析包名，版本，文件压缩格式
     * @param uri 请求地址
     * @return map<String, String> 以map格式返回信息
     * @exception
     */
    fun getUriArgs(uri: String): Map<String, String> {
        try {
            val regex = "^/([a-zA-Z0-9]+)-([\\d.]+?).(tar|zip|tar.gz|tgz|whl)$"
            val matcher = Pattern.compile(regex).matcher(uri)
            while (matcher.find()) {
                return hashMapOf("filename" to matcher.group(1),
                        "version" to matcher.group(2),
                        "format" to matcher.group(3))
            }
        } catch (e: Exception) {
            throw ComposerUnSupportCompressException("Can not support compress format!")
        }
        return mapOf()
    }
}
