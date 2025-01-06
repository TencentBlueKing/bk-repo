package com.tencent.bkrepo.preview.service

import com.tencent.bkrepo.preview.pojo.FileAttribute
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service

@Service
class FilePreviewFactory(private val context: ApplicationContext) {
    operator fun get(fileAttribute: FileAttribute): FilePreview {
        return context.getBean(fileAttribute.type!!.instanceName, FilePreview::class.java)
    }
}