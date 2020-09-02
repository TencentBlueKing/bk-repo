package com.tencent.bkrepo.repository.util

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.pojo.Page
import org.springframework.data.domain.PageRequest

/**
 * 分页工具类
 */
object Pages {

    /**
     * 根据页码[page]和分页大小[size]构造[PageRequest]
     *
     * [page]从1开始，如果传入值小于1则置为1
     * [size]如果小于0则置为默认分页大小20
     *
     * [PageRequest]要求页码从0开始
     */
    fun ofRequest(page: Int, size: Int): PageRequest {
        val pageNumber = if (page <= 0) DEFAULT_PAGE_NUMBER else page
        val pageSize = if (page < 0) DEFAULT_PAGE_SIZE else size
        return PageRequest.of(pageNumber - 1, pageSize)
    }

    inline fun <reified T> ofResponse(pageRequest: PageRequest, totalRecords: Long, records: List<T>): Page<T> {
        return Page(
            pageNumber = pageRequest.pageNumber + 1,
            pageSize = pageRequest.pageSize,
            totalRecords = totalRecords,
            records = records
        )
    }
}