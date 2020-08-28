package com.tencent.bkrepo.common.artifact.util.http

import com.tencent.bkrepo.common.artifact.stream.Range
import org.springframework.http.HttpHeaders
import java.util.regex.Pattern
import javax.servlet.http.HttpServletRequest

/**
 * Http Range请求工具类
 */
object HttpRangeUtils {

    private val RANGE_HEADER_PATTERN = Pattern.compile("bytes=(\\d+)?-(\\d+)?")

    /**
     * 从[request]中解析Range，[total]代表总长度
     */
    fun resolveRange(request: HttpServletRequest, total: Long): Range {
        val rangeHeader = request.getHeader(HttpHeaders.RANGE)?.trim()
        if (rangeHeader.isNullOrEmpty()) return Range.full(total)
        val matcher = RANGE_HEADER_PATTERN.matcher(rangeHeader)
        require(matcher.matches()) { "Invalid range header: $rangeHeader" }
        require(matcher.groupCount() >= 1) { "Invalid range header: $rangeHeader" }
        return if (matcher.group(1).isNullOrEmpty()) {
            val start = total - matcher.group(2).toLong()
            val end = total - 1
            Range(start, end, total)
        } else {
            val start = matcher.group(1).toLong()
            val end = if (matcher.group(2).isNullOrEmpty()) total - 1 else matcher.group(2).toLong()
            Range(start, end, total)
        }
    }
}