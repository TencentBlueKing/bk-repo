package com.tencent.bkrepo.media.live.config

import com.tencent.bkrepo.common.artifact.config.ArtifactConfigurerSupport
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurityCustomizer
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.media.live.config.repository.MediaLiveLocalRepository
import com.tencent.bkrepo.media.live.config.repository.MediaLiveRemoteRepository
import com.tencent.bkrepo.media.live.config.repository.MediaLiveVirtualRepository
import org.springframework.context.annotation.Configuration

@Configuration
class MediaLiveArtifactConfigurer : ArtifactConfigurerSupport() {

    override fun getRepositoryType() = RepositoryType.MEDIA
    override fun getLocalRepository() = SpringContextUtils.getBean<MediaLiveLocalRepository>()
    override fun getRemoteRepository() = SpringContextUtils.getBean<MediaLiveRemoteRepository>()
    override fun getVirtualRepository() = SpringContextUtils.getBean<MediaLiveVirtualRepository>()

    override fun getAuthSecurityCustomizer() =
        HttpAuthSecurityCustomizer { httpAuthSecurity -> httpAuthSecurity.withPrefix("/media-live") }
}