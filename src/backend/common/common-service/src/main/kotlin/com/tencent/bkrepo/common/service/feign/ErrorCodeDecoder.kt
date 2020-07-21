package com.tencent.bkrepo.common.service.feign

import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.service.exception.ExternalErrorCodeException
import feign.Response
import feign.codec.ErrorDecoder
import java.io.IOException

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
                val response = JsonUtils.objectMapper.readValue<com.tencent.bkrepo.common.api.pojo.Response<Any>>(feignResponse.body().asInputStream())
                ExternalErrorCodeException(
                    methodKey,
                    response.code,
                    response.message
                )
            } catch (exception: IOException) {
                delegate.decode(methodKey, feignResponse)
            }
        }
        return delegate.decode(methodKey, feignResponse)
    }

    companion object {
        private const val BAD_REQUEST = 400
    }
}
