package com.tencent.bkrepo.common.storage.config

data class UploadProperties(
    /**
     * 文件上传临时目录
     */
    var location: String = System.getProperty("java.io.tmpdir")
)
