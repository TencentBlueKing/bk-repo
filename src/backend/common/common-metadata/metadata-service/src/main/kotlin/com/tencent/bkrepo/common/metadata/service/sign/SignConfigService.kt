package com.tencent.bkrepo.common.metadata.service.sign

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.metadata.pojo.sign.SignConfig
import com.tencent.bkrepo.common.metadata.pojo.sign.SignConfigCreateRequest
import com.tencent.bkrepo.common.metadata.pojo.sign.SignConfigListOption
import com.tencent.bkrepo.common.metadata.pojo.sign.SignConfigUpdateRequest

/**
 * 签名配置服务接口
 */
interface SignConfigService {

    /**
     * 创建签名配置
     */
    fun create(request: SignConfigCreateRequest): SignConfig

    /**
     * 根据项目ID查询签名配置
     */
    fun find(projectId: String): SignConfig?

    /**
     * 更新签名配置
     */
    fun update(request: SignConfigUpdateRequest): SignConfig

    /**
     * 根据项目ID删除签名配置
     */
    fun delete(projectId: String): Boolean

    /**
     * 检查项目ID是否存在签名配置
     */
    fun exists(projectId: String): Boolean

    /**
     * 分页查询所有签名配置
     * @param pageNumber 页码，从1开始
     * @param pageSize 每页大小
     */
    fun findPage(option: SignConfigListOption): Page<SignConfig>
}
