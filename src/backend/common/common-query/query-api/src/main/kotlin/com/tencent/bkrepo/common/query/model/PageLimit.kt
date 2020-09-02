package com.tencent.bkrepo.common.query.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.util.CompatibleUtils
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("分页参数")
data class PageLimit(
    @ApiModelProperty("当前页")
    val pageNumber: Int = DEFAULT_PAGE_NUMBER,
    @ApiModelProperty("每页数量")
    val pageSize: Int = DEFAULT_PAGE_SIZE,

    @Deprecated("Replace with pageNumber", replaceWith = ReplaceWith("pageNumber"))
    @ApiModelProperty("当前页")
    val current: Int? = null,
    @Deprecated("Replace with pageSize", replaceWith = ReplaceWith("pageSize"))
    @ApiModelProperty("每页数量")
    val size: Int? = null
) {
    @JsonIgnore
    fun getNormalizedPageNumber(): Int {
        val pageNumber = CompatibleUtils.getValue(pageNumber, current, "PageLimit.current")
        return if (pageNumber <= 0) DEFAULT_PAGE_NUMBER else pageNumber
    }

    @JsonIgnore
    fun getNormalizedPageSize(): Int {
        val pageSize = CompatibleUtils.getValue(pageSize, size, "PageLimit.size")
        return if (pageSize <= 0) DEFAULT_PAGE_SIZE else pageSize
    }
}
