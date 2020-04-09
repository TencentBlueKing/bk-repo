package com.tencent.bkrepo.common.artifact.resolve

import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileMapMethodArgumentResolver
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileMethodArgumentResolver
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoMethodArgumentResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.multipart.commons.CommonsMultipartResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class ResolverConfiguration {

    @Bean
    fun artifactArgumentResolveConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
                resolvers.add(ArtifactInfoMethodArgumentResolver())
                resolvers.add(ArtifactFileMethodArgumentResolver())
                resolvers.add(ArtifactFileMapMethodArgumentResolver())
            }
        }
    }

    /**
     * 使用commons-fileupload, 以实现ArtifactFile接口对MultipartFile的适配
     * springboot默认使用的是Servlet3+ StandardMultipartFile
     */
    @Bean(name = ["multipartResolver"])
    fun commonsMultipartResolver(): CommonsMultipartResolver {
        val multipartResolver = CommonsMultipartResolver()
        // 通用制品库文件大小范围不固定，因此不做大小限制
        multipartResolver.setMaxUploadSize(UNLIMITED)
        multipartResolver.setMaxUploadSizePerFile(UNLIMITED)
        return multipartResolver
    }

    companion object {
        private const val UNLIMITED = -1L
    }
}
