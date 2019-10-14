package com.tencent.bkrepo.repository.util

import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException

/**
 * 节点相关工具类
 * path节点目录命名规则：以'/'开头，以'/'结尾
 * name节点文件命名规则：不含'/'
 * fullpath全路径命名规则：以'/'开头，结尾不含'/'
 *
 * @author: carrypan
 * @date: 2019-09-24
 */
object NodeUtils {

    private val forbiddenNameList = listOf(".", "..")

    private val keywordList = listOf("\\", "$", "(", ")", "*", "+", ".", "[", "]", "?", "^", "{", "}", "|", "?", "&")
    /**
     * 最大目录深度
     */
    private const val MAX_DIR_DEPTH = 10

    /**
     * 文件名称最大长度
     */
    private const val MAX_FILENAME_LENGTH = 128

    /**
     * 文件分隔符
     */
    private const val FILE_SEPARATOR = "/"

    /**
     * 文件分隔符
     */
    private const val DOT = "."

    /**
     * 文件分隔字符
     */
    private const val FILE_SEPARATOR_CHAR = '/'

    /**
     * 根目录
     */
    const val ROOT_DIR = FILE_SEPARATOR

    /**
     * 格式化目录名称, 返回格式/a/b/c/，根目录返回/
     */
    fun formatPath(input: String): String {
        if (isRootDir(input)) return ROOT_DIR

        val nameList = input.split(FILE_SEPARATOR).filter { it.isNotBlank() }.map { it.trim() }.toList()
        val builder = StringBuilder()
        nameList.forEach { builder.append(FILE_SEPARATOR).append(it) }
        return builder.append(FILE_SEPARATOR).toString()
    }

    /**
     * 格式化全路径名称, 返回格式/a/b/c，根目录返回/
     */
    fun formatFullPath(input: String): String {
        if (isRootDir(input)) return ROOT_DIR

        val nameList = input.split(FILE_SEPARATOR).filter { it.isNotBlank() }.map { it.trim() }.toList()
        val builder = StringBuilder()
        nameList.forEach { builder.append(FILE_SEPARATOR).append(it) }
        return builder.toString()
    }

    /**
     * 解析目录名称，返回格式/a/b/c/，根目录返回/
     * 出错则抛出异常
     */
    fun parseDirName(input: String): String {
        val dirName = input.trim()
        dirName.takeIf { it.startsWith(FILE_SEPARATOR) }
                ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "Directory name {$dirName} is invalid, it should start with '$FILE_SEPARATOR'.")

        val nameList = dirName.split(FILE_SEPARATOR).filter { it.isNotBlank() }.map { parseFileName(it) }.toList()
        nameList.takeIf { it.size <= MAX_DIR_DEPTH }
                ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "The depth of directory should not exceed $MAX_DIR_DEPTH.")

        val builder = StringBuilder()
        nameList.forEach { builder.append(FILE_SEPARATOR).append(it) }
        return builder.append(FILE_SEPARATOR).toString()
    }

    /**
     * 处理并验证文件名称，返回格式abc.txt
     * 不能包含/，不能全为空，不能超过指定长度
     */
    fun parseFileName(input: String): String {
        val fileName = input.trim()
        fileName.takeIf { it.isNotBlank() }
                ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "File name can not be blank.")
        fileName.takeUnless { forbiddenNameList.contains(it) }
                ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID)
        fileName.takeUnless { it.contains(FILE_SEPARATOR) }
                ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "File name {$input} should not contain '$FILE_SEPARATOR'.")
        fileName.takeIf { it.length <= MAX_FILENAME_LENGTH }
                ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "The length of name {$input} should not exceed $MAX_FILENAME_LENGTH.")
        return fileName
    }

    /**
     * 根据路径列表和文件名组合全路径，返回格式/a/b/c/abc.txt
     */
    fun combineFullPath(path: String, name: String): String {
        return if (!path.endsWith(FILE_SEPARATOR)) path + FILE_SEPARATOR + name else path + name
    }

    /**
     * 获取父级目录，返回格式/a/b/c/
     */
    fun getParentPath(path: String): String {
        val index = path.trimEnd(FILE_SEPARATOR_CHAR).lastIndexOf(FILE_SEPARATOR)
        return if (isRootDir(path) || index <= 0) ROOT_DIR else path.substring(0, index + 1)
    }

    /**
     * 根据目录获取文件名称，返回格式abc.txt
     */
    fun getName(path: String): String {
        val trimmedPath = path.trimEnd(FILE_SEPARATOR_CHAR)
        return if (isRootDir(trimmedPath)) "" else trimmedPath.substring(trimmedPath.lastIndexOf(FILE_SEPARATOR) + 1)
    }

    /**
     * 判断路径是否为根目录
     */
    fun isRootDir(path: String): Boolean {
        return path == ROOT_DIR || path == ""
    }

    /**
     * 正则特殊符号转义
     */
    fun escapeRegex(input: String): String {
        var escapedString = input.trim()
        if (escapedString.isNotBlank()) {
            keywordList.forEach {
                if (escapedString.contains(it)) {
                    escapedString = escapedString.replace(it, "\\$it")
                }
            }
        }
        return escapedString
    }

    /**
     * 获取文件后缀
     */
    fun getExtension(fileName: String): String? {
        return fileName.trim().substring(fileName.lastIndexOf(DOT) + 1)
    }
}
