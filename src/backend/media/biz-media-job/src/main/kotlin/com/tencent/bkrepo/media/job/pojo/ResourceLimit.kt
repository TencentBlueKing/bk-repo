package com.tencent.bkrepo.media.job.pojo

data class ResourceLimit(
    val limitMem: String = "4GB",
    val limitStorage: String = "128GB",
    val limitCpu: String = "16",
    val requestMem: String = "2GB",
    val requestStorage: String = "16GB",
    val requestCpu: String = "4",
)
