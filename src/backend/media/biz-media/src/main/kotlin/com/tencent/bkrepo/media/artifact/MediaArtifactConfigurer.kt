package com.tencent.bkrepo.media.artifact

import com.tencent.bkrepo.common.artifact.config.ArtifactConfigurerSupport
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurityCustomizer
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.media.artifact.repository.MediaLocalRepository
import com.tencent.bkrepo.media.artifact.repository.MediaRemoteRepository
import com.tencent.bkrepo.media.artifact.repository.MediaVirtualRepository
import com.tencent.bkrepo.media.config.MediaProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(MediaProperties::class)
class MediaArtifactConfigurer : ArtifactConfigurerSupport() {

    override fun getRepositoryType() = RepositoryType.MEDIA
    override fun getLocalRepository() = SpringContextUtils.getBean<MediaLocalRepository>()
    override fun getRemoteRepository() = SpringContextUtils.getBean<MediaRemoteRepository>()
    override fun getVirtualRepository() = SpringContextUtils.getBean<MediaVirtualRepository>()

    override fun getAuthSecurityCustomizer() =
        HttpAuthSecurityCustomizer { httpAuthSecurity -> httpAuthSecurity.withPrefix("/media") }
}
