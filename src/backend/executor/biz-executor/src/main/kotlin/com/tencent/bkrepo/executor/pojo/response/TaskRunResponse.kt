package com.tencent.bkrepo.executor.pojo.response

data class TaskRunResponse (
    var totalRecords: Long,
    var records: List<FileRunResponse>
)
