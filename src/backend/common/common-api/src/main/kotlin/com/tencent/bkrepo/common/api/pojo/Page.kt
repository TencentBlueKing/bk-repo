package com.tencent.bkrepo.common.api.pojo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import kotlin.math.ceil

@ApiModel("分页数据包装模型")
data class Page<out T>(
    @ApiModelProperty("页码(从1页开始)")
    val pageNumber: Int,
    @ApiModelProperty("每页多少条")
    val pageSize: Int,
    @ApiModelProperty("总记录条数")
    val totalRecords: Long,
    @ApiModelProperty("总页数")
    val totalPages: Long,
    @ApiModelProperty("数据列表")
    val records: List<T>
) {
    constructor(pageNumber: Int, pageSize: Int, totalRecords: Long, records: List<T>) : this(
        pageNumber = pageNumber,
        pageSize = pageSize,
        totalRecords = totalRecords,
        totalPages = ceil(totalRecords * 1.0 / pageSize).toLong(),
        records = records
    )

    /**
     * 兼容处理
     */
    @Deprecated("Will be removed", replaceWith = ReplaceWith("totalRecords"))
    fun getCount(): Long = this.totalRecords

    @Deprecated("Will be removed", replaceWith = ReplaceWith("pageNumber"))
    fun getPage(): Int = this.pageNumber
}
