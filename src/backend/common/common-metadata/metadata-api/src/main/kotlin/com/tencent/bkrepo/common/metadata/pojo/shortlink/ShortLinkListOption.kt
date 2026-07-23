package com.tencent.bkrepo.common.metadata.pojo.shortlink

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE

/**
 * 按创建人分页查询短链接选项
 */
data class ShortLinkListOption(
    /**
     * 创建人
     */
    val createdBy: String,
    /**
     * 页码，从 1 开始
     */
    val pageNumber: Int = DEFAULT_PAGE_NUMBER,
    /**
     * 每页大小
     */
    val pageSize: Int = DEFAULT_PAGE_SIZE,
)
