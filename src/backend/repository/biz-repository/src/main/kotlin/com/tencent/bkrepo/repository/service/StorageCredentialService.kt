package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.pojo.credendial.StorageCredentialsCreateRequest

/**
 * 存储凭证服务接口
 */
interface StorageCredentialService {

    /**
     * 根据[request]创建存储凭证
     */
    fun create(userId: String, request: StorageCredentialsCreateRequest)

    /**
     * 根据[key]查询存储凭证[StorageCredentials]，不存在则返回`null`
     */
    fun findByKey(key: String): StorageCredentials?

    /**
     * 查询存储凭证列表
     */
    fun list(): List<StorageCredentials>

    /**
     * 根据[key]删除存储凭证
     */
    fun delete(key: String)
}
