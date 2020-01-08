package com.tencent.bkrepo.common.service.util

import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.pojo.Response
import org.apache.skywalking.apm.toolkit.trace.TraceContext

/**
 *
 * @author: carrypan
 * @date: 2020/1/8
 */
object ResponseBuilder {
    fun <T> build(code: Int, message: String?, data: T?) = Response(code, message, data, TraceContext.traceId())

    fun success() = build(CommonMessageCode.SUCCESS.getCode(), null, null)

    fun <T> success(data: T) = build(CommonMessageCode.SUCCESS.getCode(), null, data)

    fun fail(code: Int, message: String) = build(code, message, null)
}
