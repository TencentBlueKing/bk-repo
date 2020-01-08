package com.tencent.bkrepo.common.service.exception

import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bkrepo.common.api.util.JsonUtils
import feign.Response
import feign.codec.ErrorDecoder

/**
 * Feign ErrorDecoder
 *
 * @author: carrypan
 * @date: 2019/12/10
 */
class ErrorCodeDecoder : ErrorDecoder {

    private val delegate: ErrorDecoder = ErrorDecoder.Default()

    override fun decode(methodKey: String, feignResponse: Response): Exception {
        if (feignResponse.status() == BAD_REQUEST) {
            return try {
                val response = JsonUtils.objectMapper.readValue<com.tencent.bkrepo.common.api.pojo.Response<Any>>(feignResponse.body().asReader())
                ExternalErrorCodeException(
                    methodKey,
                    response.code,
                    response.message
                )
            } catch (exception: Exception) {
                delegate.decode(methodKey, feignResponse)
            }
        }
        return delegate.decode(methodKey, feignResponse)
    }

    companion object {
        private const val BAD_REQUEST = 400
    }
}
