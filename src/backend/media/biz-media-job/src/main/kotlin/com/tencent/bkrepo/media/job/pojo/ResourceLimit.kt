package com.tencent.bkrepo.media.job.pojo

data class ResourceLimit(
    val limitMem: String = "8G",
    val limitStorage: String = "128G",
    val limitCpu: String = "16",
    val requestMem: String = "4G",
    val requestStorage: String = "16G",
    val requestCpu: String = "4",
)
