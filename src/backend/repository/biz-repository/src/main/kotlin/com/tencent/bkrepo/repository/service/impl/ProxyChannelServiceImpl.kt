package com.tencent.bkrepo.repository.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.repository.dao.repository.ProxyChannelRepository
import com.tencent.bkrepo.repository.model.TProxyChannel
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelCreateRequest
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelInfo
import com.tencent.bkrepo.repository.service.ProxyChannelService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

/**
 * 代理源服务实现类
 */
@Service
class ProxyChannelServiceImpl(
    private val proxyChannelRepository: ProxyChannelRepository
) : ProxyChannelService {

    override fun findById(id: String): ProxyChannelInfo? {
        val tProxyChannel = proxyChannelRepository.findByIdOrNull(id)
        return convert(tProxyChannel)
    }

    override fun create(userId: String, request: ProxyChannelCreateRequest) {
        with(request) {
            Preconditions.checkArgument(public, this::public.name)
            Preconditions.checkNotBlank(name, this::name.name)
            Preconditions.checkArgument(checkExistByName(name, repoType), this::name.name)
            Preconditions.checkArgument(checkExistByUrl(url, repoType), this::url.name)
            val tProxyChannel = TProxyChannel(
                public = public,
                name = name.trim(),
                url = formatUrl(url),
                repoType = repoType,
                credentialKey = credentialKey,
                username = username,
                password = password
            )
            proxyChannelRepository.insert(tProxyChannel)
        }
    }

    override fun listPublicChannel(repoType: RepositoryType): List<ProxyChannelInfo> {
        return proxyChannelRepository.findByPublicAndRepoType(true, repoType).map { convert(it)!! }
    }

    override fun checkExistById(id: String, repoType: RepositoryType): Boolean {
        if (id.isBlank()) return false
        return proxyChannelRepository.findByIdAndRepoType(id, repoType) != null
    }

    override fun checkExistByName(name: String, repoType: RepositoryType): Boolean {
        if (name.isBlank()) return false
        return proxyChannelRepository.findByNameAndRepoType(name, repoType) != null
    }

    override fun checkExistByUrl(url: String, repoType: RepositoryType): Boolean {
        if (url.isBlank()) return false
        return proxyChannelRepository.findByUrlAndRepoType(formatUrl(url), repoType) != null
    }

    private fun formatUrl(url: String): String {
        return try {
            UrlFormatter.formatUrl(url)
        } catch (exception: IllegalArgumentException) {
            throw ErrorCodeException(CommonMessageCode.PARAMETER_INVALID, "url")
        }
    }

    companion object {

        private fun convert(tProxyChannel: TProxyChannel?): ProxyChannelInfo? {
            return tProxyChannel?.let {
                ProxyChannelInfo(
                    id = it.id!!,
                    public = it.public,
                    name = it.name,
                    url = it.url,
                    repoType = it.repoType,
                    credentialKey = it.credentialKey,
                    username = it.username,
                    password = it.password
                )
            }
        }
    }
}
