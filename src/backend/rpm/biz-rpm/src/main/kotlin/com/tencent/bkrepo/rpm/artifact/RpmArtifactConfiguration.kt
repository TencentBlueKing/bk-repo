package com.tencent.bkrepo.rpm.artifact

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.config.ArtifactConfiguration
import com.tencent.bkrepo.common.artifact.exception.ExceptionResponseTranslator
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.rpm.pojo.RpmExceptionResponse
import org.springframework.context.annotation.Bean
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component

@Component
class RpmArtifactConfiguration : ArtifactConfiguration {
    override fun getRepositoryType(): RepositoryType = RepositoryType.RPM

    @Bean
    fun exceptionResponseTranslator() = object : ExceptionResponseTranslator {
        override fun translate(payload: Response<*>, request: ServerHttpRequest, response: ServerHttpResponse): Any {
            return RpmExceptionResponse(StringPool.EMPTY, payload.message.orEmpty())
        }
    }
}
