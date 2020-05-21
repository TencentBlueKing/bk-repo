package com.tencent.bkrepo.common.artifact.resolve.file

import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties
import org.springframework.util.unit.DataSize

class UploadConfigElement(multipartProperties: MultipartProperties) {
    val location: String = multipartProperties.location ?: System.getProperty("java.io.tmpdir")
    val maxFileSize: Long = convertToBytes(multipartProperties.maxFileSize, -1)
    val maxRequestSize: Long = convertToBytes(multipartProperties.maxRequestSize, -1)
    val fileSizeThreshold: Int = convertToBytes(multipartProperties.maxRequestSize, 0).toInt()
    val resolveLazily: Boolean = multipartProperties.isResolveLazily

    private fun convertToBytes(size: DataSize?, defaultValue: Int): Long {
        return if (size?.isNegative == false) size.toBytes() else defaultValue.toLong()
    }
}