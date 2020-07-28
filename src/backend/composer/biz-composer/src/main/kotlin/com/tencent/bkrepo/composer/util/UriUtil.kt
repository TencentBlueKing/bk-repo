package com.tencent.bkrepo.composer.util

import com.tencent.bkrepo.composer.exception.ComposerUnSupportCompressException
import com.tencent.bkrepo.composer.util.pojo.UriArgs
import java.util.regex.Pattern

object UriUtil {
    /**
     * 根据uri解析包名，版本，文件压缩格式
     * @param uri 请求地址
     * @return map<String, String> 以map格式返回信息
     * @exception
     */
    fun getUriArgs(uri: String): UriArgs {
        val regex = "^/([a-zA-Z0-9]+)-([\\d.]+?).(tar|zip|tar.gz|tgz|whl)$"
        val matcher = Pattern.compile(regex).matcher(uri)
        while (matcher.find()) {
            return UriArgs(matcher.group(1), matcher.group(2), matcher.group(3))
        }
        throw ComposerUnSupportCompressException("Can not support compress format!")
    }
}
