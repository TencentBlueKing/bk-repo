package com.tencent.bkrepo.common.metadata.pojo.sign

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE

data class SignConfigListOption(
    val projectId: String? = null,
    val pageNumber: Int = DEFAULT_PAGE_NUMBER,
    val pageSize: Int = DEFAULT_PAGE_SIZE
)
