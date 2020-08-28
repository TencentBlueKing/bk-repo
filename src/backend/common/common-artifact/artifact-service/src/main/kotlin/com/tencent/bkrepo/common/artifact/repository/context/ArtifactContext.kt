package com.tencent.bkrepo.common.artifact.repository.context

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.api.constant.USER_KEY
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.constant.ARTIFACT_INFO_KEY
import com.tencent.bkrepo.common.artifact.constant.REPO_KEY
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.configuration.RepositoryConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.composite.CompositeConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.local.LocalConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.virtual.VirtualConfiguration
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.pojo.repo.RepositoryInfo
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 构件上下文
 */
open class ArtifactContext(repo: RepositoryInfo? = null) {
    private var contextAttributes: MutableMap<String, Any> = mutableMapOf()
    val request: HttpServletRequest = HttpContextHolder.getRequest()
    val response: HttpServletResponse = HttpContextHolder.getResponse()
    val userId: String
    val artifactInfo: ArtifactInfo
    var repositoryInfo: RepositoryInfo
    val storageCredentials: StorageCredentials? get() = repositoryInfo.storageCredentials
    val projectId: String get() = repositoryInfo.projectId
    val repoName: String get() = repositoryInfo.name

    init {
        this.userId = request.getAttribute(USER_KEY) as? String ?: ANONYMOUS_USER
        this.artifactInfo = request.getAttribute(ARTIFACT_INFO_KEY) as ArtifactInfo
        this.repositoryInfo = repo ?: request.getAttribute(REPO_KEY) as RepositoryInfo
    }

    /**
     * 使用当前实例的属性，拷贝出一个新的[ArtifactContext]实例
     * 传入的[repositoryInfo]会替换当前实例的仓库信息
     */
    fun copy(repositoryInfo: RepositoryInfo): ArtifactContext {
        val context = this.javaClass.newInstance()
        context.repositoryInfo = repositoryInfo
        context.contextAttributes = this.contextAttributes
        return context
    }

    /**
     * 添加上下文属性
     */
    fun putAttribute(key: String, value: Any) {
        this.contextAttributes[key] = value
    }

    /**
     * 根据属性名[key]获取字符串类型属性
     */
    fun getStringAttribute(key: String): String? {
        return this.contextAttributes[key]?.toString()
    }

    /**
     * 根据属性名[key]获取Boolean类型属性
     */
    fun getBooleanAttribute(key: String): Boolean? {
        return this.contextAttributes[key]?.toString()?.toBoolean()
    }

    /**
     * 根据属性名[key]获取整数类型属性
     */
    fun getIntegerAttribute(key: String): Int? {
        return this.contextAttributes[key]?.toString()?.toInt()
    }

    /**
     * 根据属性名[key]获取属性
     */
    fun getAttribute(key: String): Any? {
        return this.contextAttributes[key]
    }

    /**
     * 获取仓库配置
     */
    fun getConfiguration(): RepositoryConfiguration {
        return this.repositoryInfo.configuration
    }

    /**
     * 获取本地仓库配置
     */
    fun getLocalConfiguration(): LocalConfiguration {
        require(this.repositoryInfo.category == RepositoryCategory.LOCAL)
        return this.repositoryInfo.configuration as LocalConfiguration
    }

    /**
     * 获取远程仓库配置
     */
    fun getRemoteConfiguration(): RemoteConfiguration {
        require(this.repositoryInfo.category == RepositoryCategory.REMOTE)
        return this.repositoryInfo.configuration as RemoteConfiguration
    }

    /**
     * 获取远程仓库配置
     */
    fun getVirtualConfiguration(): VirtualConfiguration {
        require(this.repositoryInfo.category == RepositoryCategory.VIRTUAL)
        return this.repositoryInfo.configuration as VirtualConfiguration
    }

    /**
     * 获取组合仓库配置
     */
    fun getCompositeConfiguration(): CompositeConfiguration {
        require(this.repositoryInfo.category == RepositoryCategory.COMPOSITE)
        return this.repositoryInfo.configuration as CompositeConfiguration
    }
}
