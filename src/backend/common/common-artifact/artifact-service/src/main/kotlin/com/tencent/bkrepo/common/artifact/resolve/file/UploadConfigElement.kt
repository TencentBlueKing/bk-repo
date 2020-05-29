package com.tencent.bkrepo.common.artifact.resolve.file

import org.springframework.util.unit.DataSize

class UploadConfigElement(uploadProperties: UploadProperties) {
    val location: String = uploadProperties.location
    val maxFileSize: Long = convertToBytes(uploadProperties.maxFileSize, -1)
    val maxRequestSize: Long = convertToBytes(uploadProperties.maxRequestSize, -1)
    val fileSizeThreshold: Int = convertToBytes(uploadProperties.fileSizeThreshold, 0).toInt()
    val resolveLazily: Boolean = uploadProperties.isResolveLazily

    private fun convertToBytes(size: DataSize?, defaultValue: Int): Long {
        return if (size?.isNegative == false) size.toBytes() else defaultValue.toLong()
    }
}
