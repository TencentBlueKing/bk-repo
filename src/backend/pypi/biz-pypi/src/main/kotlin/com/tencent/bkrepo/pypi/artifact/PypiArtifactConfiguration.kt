package com.tencent.bkrepo.pypi.artifact

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.exception.ExceptionResponseTranslator
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.pypi.pojo.PypiExceptionResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse

/**
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Configuration
class PypiArtifactConfiguration : ArtifactConfiguration {

    override fun getRepositoryType() = RepositoryType.PYPI

    @Bean
    fun exceptionResponseTranslator() = object : ExceptionResponseTranslator {
        override fun translate(payload: Response<*>, request: ServerHttpRequest, response: ServerHttpResponse): Any {
            return PypiExceptionResponse(StringPool.EMPTY, payload.message.orEmpty())
        }
    }
}
