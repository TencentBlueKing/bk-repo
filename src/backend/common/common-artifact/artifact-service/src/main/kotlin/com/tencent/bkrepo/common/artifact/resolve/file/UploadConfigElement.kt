package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.storage.core.StorageProperties
import org.springframework.util.unit.DataSize
import javax.servlet.MultipartConfigElement

class UploadConfigElement(
    private val storageProperties: StorageProperties
) : MultipartConfigElement(storageProperties.defaultStorageCredentials().upload.location) {

    init {
        if (storageProperties.maxFileSize.isNegative) {
            storageProperties.maxFileSize = DataSize.ofBytes(-1)
        }
        if (storageProperties.maxRequestSize.isNegative) {
            storageProperties.maxRequestSize = DataSize.ofBytes(-1)
        }
        if (storageProperties.fileSizeThreshold.isNegative) {
            storageProperties.maxRequestSize = DataSize.ofBytes(-1)
        }
    }

    override fun getLocation(): String {
        return storageProperties.defaultStorageCredentials().upload.location
    }

    override fun getMaxFileSize(): Long {
        return storageProperties.maxFileSize.toBytes()
    }

    override fun getMaxRequestSize(): Long {
        return storageProperties.maxRequestSize.toBytes()
    }

    override fun getFileSizeThreshold(): Int {
        return storageProperties.fileSizeThreshold.toBytes().toInt()
    }
}
