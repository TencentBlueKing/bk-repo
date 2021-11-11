package com.tencent.bkrepo.oci.artifact

import com.tencent.bkrepo.common.artifact.config.ArtifactConfigurerSupport
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.repository.virtual.VirtualRepository
import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurity
import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurityCustomizer
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.oci.artifact.auth.OciLoginAuthHandler
import com.tencent.bkrepo.oci.artifact.repository.OciRegistryLocalRepository
import com.tencent.bkrepo.oci.artifact.repository.OciRegistryRemoteRepository
import com.tencent.bkrepo.oci.artifact.repository.OciRegistryVirtualRepository
import org.springframework.context.annotation.Configuration

@Configuration
class OciRegistryArtifactConfigurer : ArtifactConfigurerSupport() {
    override fun getRepositoryType(): RepositoryType = RepositoryType.OCI

    override fun getLocalRepository(): LocalRepository = SpringContextUtils.getBean<OciRegistryLocalRepository>()

    override fun getRemoteRepository(): RemoteRepository = SpringContextUtils.getBean<OciRegistryRemoteRepository>()

    override fun getVirtualRepository(): VirtualRepository = SpringContextUtils.getBean<OciRegistryVirtualRepository>()

    override fun getAuthSecurityCustomizer(): HttpAuthSecurityCustomizer = object : HttpAuthSecurityCustomizer {
        override fun customize(httpAuthSecurity: HttpAuthSecurity) {
            val authenticationManager = httpAuthSecurity.authenticationManager!!
            val helmLoginAuthHandler = OciLoginAuthHandler(authenticationManager)
            httpAuthSecurity.withPrefix("/oci").addHttpAuthHandler(helmLoginAuthHandler)
        }
    }
}
