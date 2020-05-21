package com.tencent.bkrepo.common.artifact.resolve

import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileMapMethodArgumentResolver
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileMethodArgumentResolver
import com.tencent.bkrepo.common.artifact.resolve.file.UploadConfigElement
import com.tencent.bkrepo.common.artifact.resolve.path.ArtifactInfoMethodArgumentResolver
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.io.FileSystemResource
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.multipart.commons.CommonsMultipartResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@Import(ArtifactFileFactory::class)
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
    fun commonsMultipartResolver(multipartProperties: MultipartProperties): CommonsMultipartResolver {
        val config = UploadConfigElement(multipartProperties)
        val multipartResolver = CommonsMultipartResolver()
        multipartResolver.setMaxUploadSize(config.maxRequestSize)
        multipartResolver.setMaxUploadSizePerFile(config.maxFileSize)
        multipartResolver.setUploadTempDir(FileSystemResource(config.location))
        multipartResolver.setResolveLazily(config.resolveLazily)
        multipartResolver.setMaxInMemorySize(config.fileSizeThreshold)
        return multipartResolver
    }
}
