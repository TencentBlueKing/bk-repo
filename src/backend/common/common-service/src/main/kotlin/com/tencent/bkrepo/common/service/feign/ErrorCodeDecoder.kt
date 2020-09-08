package com.tencent.bkrepo.common.service.feign

import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.service.exception.ExternalErrorCodeException
import feign.Response
import feign.codec.ErrorDecoder
import java.io.IOException

/**
 * Feign ErrorDecoder
 */
class ErrorCodeDecoder : ErrorDecoder {

    private val delegate: ErrorDecoder = ErrorDecoder.Default()

    override fun decode(methodKey: String, feignResponse: Response): Exception {
        if (feignResponse.status() == HttpStatus.BAD_REQUEST.value) {
            return try {
                feignResponse.body().asInputStream().use {
                    val response = JsonUtils.objectMapper.readValue<com.tencent.bkrepo.common.api.pojo.Response<Any>>(it)
                    ExternalErrorCodeException(methodKey, response.code, response.message)
                }
            } catch (ignored: IOException) {
                delegate.decode(methodKey, feignResponse)
            }
        }
        return delegate.decode(methodKey, feignResponse)
    }
}
