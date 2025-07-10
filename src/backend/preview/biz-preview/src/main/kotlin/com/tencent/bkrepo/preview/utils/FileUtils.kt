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

import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils
import org.springframework.util.StringUtils
import org.springframework.web.util.HtmlUtils
import java.io.File
import java.net.URL
import java.util.Locale

object FileUtils {
    private val logger = LoggerFactory.getLogger(FileUtils::class.java)
    const val DEFAULT_FILE_ENCODING = "UTF-8"
    private val illegalFileStrList: MutableList<String> = ArrayList()

    /**
     * 检查文件名是否合规
     *
     * @param fileName 文件名
     * @return 合规结果, true:不合规，false:合规
     */
    fun isIllegalFileName(fileName: String): Boolean {
        for (str in illegalFileStrList) {
            if (fileName.contains(str)) {
                return true
            }
        }
        return false
    }

    /**
     * 检查是否是数字
     *
     * @param str 文件名
     * @return 合规结果, true:不合规，false:合规
     */
    fun isInteger(str: String?): Boolean {
        return !str.isNullOrBlank() && str.matches("-?[0-9]+\\.?[0-9]*".toRegex())
    }


    /**
     * 判断url是否是http资源
     *
     * @param url url
     * @return 是否http
     */
    fun isHttpUrl(url: URL): Boolean {
        return url.protocol.lowercase(Locale.getDefault()).startsWith("file")
                || url.protocol.lowercase(Locale.getDefault()).startsWith("http")
    }

    /**
     * 判断url是否是ftp资源
     *
     * @param url url
     * @return 是否ftp
     */
    fun isFtpUrl(url: URL): Boolean {
        return "ftp".equals(url.protocol, ignoreCase = true)
    }

    /**
     * 删除单个文件
     *
     * @param fileName 要删除的文件的文件名
     * @return 单个文件删除成功返回true，否则返回false
     */
    fun deleteFileByName(fileName: String): Boolean {
        val file = File(fileName)
        // 如果文件路径所对应的文件存在，并且是一个文件，则直接删除
        return if (file.exists() && file.isFile) {
            if (file.delete()) {
                logger.debug("Delete a single file [$fileName] success.")
                true
            } else {
                logger.warn("Delete a single file [$fileName] fail.")
                false
            }
        } else {
            logger.info("Delete a single file [$fileName] does not exist.")
            false
        }
    }

    fun htmlEscape(input: String): String {
        if (StringUtils.hasText(input)) {
            //input = input.replaceAll("\\{", "%7B").replaceAll("}", "%7D").replaceAll("\\\\", "%5C");
            val htmlStr = HtmlUtils.htmlEscape(input, "UTF-8")
            //& -> &amp;
            return htmlStr.replace("&amp;", "&")
        }
        return input
    }

    /**
     * 通过文件名获取文件后缀
     *
     * @param fileName 文件名称
     * @return 文件后缀
     */
    fun suffixFromFileName(fileName: String): String {
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase()
    }

    /**
     * 根据文件路径删除文件
     *
     * @param filePath 绝对路径
     */
    fun deleteFileByPath(filePath: String?) {
        val file = File(filePath)
        if (file.exists() && !file.delete()) {
            logger.warn("File [$filePath] delete failed.")
        }
    }

    /**
     * 删除目录及目录下的文件
     *
     * @param dir 要删除的目录的文件路径
     * @return 目录删除成功返回true，否则返回false
     */
    fun deleteDirectory(dir: String): Boolean {
        var dirPath = dir
        // 如果 dir 不以文件分隔符结尾，自动添加文件分隔符
        if (!dirPath.endsWith(File.separator)) {
            dirPath += File.separator
        }
        val dirFile = File(dirPath)

        // 如果 dir 对应的文件不存在，或者不是一个目录，则退出
        if (!dirFile.exists() || !dirFile.isDirectory) {
            logger.warn("Failed to delete directory,[$dirPath] does not exist.")
            return false
        }

        var flag = true
        // 删除文件夹中的所有文件，包括子目录
        val files = dirFile.listFiles() ?: return false
        for (file in files) {
            if (file.isFile) {
                flag = deleteFileByName(file.absolutePath) // 自定义的删除文件方法
                if (!flag) {
                    break
                }
            } else if (file.isDirectory) {
                // 删除子目录
                flag = deleteDirectory(file.absolutePath)
                if (!flag) {
                    break
                }
            }
        }

        // 删除空目录
        if (!dirFile.delete() || !flag) {
            logger.warn("Failed to delete directory.")
            return false
        }
        return true
    }

    /**
     * 删除当前文件，同时如果当前目录为空，父目录也删除
     */
    fun deleteFileAndParentDirectory(filePath: String): Boolean {
        val file = File(filePath)
        if (file.exists()) {
            val deletedFile = file.delete()
            logger.debug("File deleted: $deletedFile")
            val parentDir = file.parentFile
            // 删除上一层目录（如果是空目录）
            if (parentDir?.exists() == true && parentDir.list().isNullOrEmpty()) {
                val deletedDir = parentDir.delete()
                logger.debug("Parent directory deleted: $deletedDir")
            }
        } else {
            logger.warn("File does not exist,path: $filePath")
        }
        return true
    }

    /**
     * 判断文件是否允许上传
     *
     * @param file 文件扩展名
     * @return 是否允许上传
     */
    fun isAllowedUpload(file: String, prohibit: String): Boolean {
        val fileType = suffixFromFileName(file)
        for (type in prohibit.split(",".toRegex()).toTypedArray()) {
            if (type == fileType) {
                return false
            }
        }
        return !ObjectUtils.isEmpty(fileType)
    }

    /**
     * 判断文件是否存在
     *
     * @param filePath 文件路径
     * @return 是否存在 true:存在，false:不存在
     */
    fun isExist(filePath: String?): Boolean {
        val file = File(filePath)
        return file.exists()
    }

    /**
     * 获取文件的m5d和大小
     *
     * @param filePath 文件绝对路径
     * @return m5d和大小
     */
    fun getFileMd5AndSize(filePath: String): Pair<String, Long>? {
        val file = File(filePath)
        if (!(file.exists() && file.isFile)) {
            logger.warn("Invalid file path: $filePath")
            return null
        }
        val md5Hex = file.inputStream().use { DigestUtils.md5Hex(it) }
        return md5Hex to file.length()
    }

    init {
        illegalFileStrList.add("../")
        illegalFileStrList.add("./")
        illegalFileStrList.add("..\\")
        illegalFileStrList.add(".\\")
        illegalFileStrList.add("\\..")
        illegalFileStrList.add("\\.")
        illegalFileStrList.add("..")
        illegalFileStrList.add("...")
    }
}