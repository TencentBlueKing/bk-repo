package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.storage.monitor.UploadProperties
import org.springframework.util.unit.DataSize
import javax.servlet.MultipartConfigElement

class UploadConfigElement(
    private val uploadProperties: UploadProperties
) : MultipartConfigElement(uploadProperties.location) {

    init {
        if (uploadProperties.maxFileSize.isNegative) {
            uploadProperties.maxFileSize = DataSize.ofBytes(-1)
        }
        if (uploadProperties.maxRequestSize.isNegative) {
            uploadProperties.maxRequestSize = DataSize.ofBytes(-1)
        }
        if (uploadProperties.fileSizeThreshold.isNegative) {
            uploadProperties.maxRequestSize = DataSize.ofBytes(-1)
        }
    }

    override fun getLocation(): String {
        return uploadProperties.location
    }

    override fun getMaxFileSize(): Long {
        return uploadProperties.maxFileSize.toBytes()
    }

    override fun getMaxRequestSize(): Long {
        return uploadProperties.maxRequestSize.toBytes()
    }

    override fun getFileSizeThreshold(): Int {
        return uploadProperties.fileSizeThreshold.toBytes().toInt()
    }

    fun isResolveLazily(): Boolean {
        return uploadProperties.isResolveLazily
    }

    fun getRateLimit(): Long {
        return uploadProperties.rateLimit.toBytes()
    }
}
