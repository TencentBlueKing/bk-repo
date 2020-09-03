package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelCreateRequest
import com.tencent.bkrepo.repository.pojo.proxy.ProxyChannelInfo

/**
 * 代理源服务接口
 */
interface ProxyChannelService {

    /**
     * 根据[id]查询代理源信息
     */
    fun findById(id: String): ProxyChannelInfo?

    /**
     * 根据[request]创建代理源
     */
    fun create(userId: String, request: ProxyChannelCreateRequest)

    /**
     * 判断id为[id]类型为[repoType]的代理源是否存在
     */
    fun checkExistById(id: String, repoType: RepositoryType): Boolean

    /**
     * 判断名称为[name]类型为[repoType]的代理源是否存在
     */
    fun checkExistByName(name: String, repoType: RepositoryType): Boolean

    /**
     * 判断url为[url]类型为[repoType]的代理源是否存在
     */
    fun checkExistByUrl(url: String, repoType: RepositoryType): Boolean

    /**
     * 列表查询公有源
     */
    fun listPublicChannel(repoType: RepositoryType): List<ProxyChannelInfo>
}
