package com.tencent.bkrepo.executor.pojo.response

import io.swagger.annotations.ApiModel

@ApiModel("文件扫描结果")
data class FileScanResponse(
    val taskId: String?,
    val result: Boolean,
    val duration: Long?
)
