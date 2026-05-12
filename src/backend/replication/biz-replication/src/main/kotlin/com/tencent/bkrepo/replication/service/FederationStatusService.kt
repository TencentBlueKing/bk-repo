package com.tencent.bkrepo.replication.service

import com.tencent.bkrepo.replication.pojo.federation.FederationMemberStatusInfo
import com.tencent.bkrepo.replication.pojo.federation.FederationRepositoryStatusInfo

/**
 * 联邦仓库状态服务接口
 */
interface FederationStatusService {
    /**
     * 获取联邦仓库状态
     *
     * @param projectId 项目ID
     * @param repoName 仓库名称
     * @param federationId 联邦ID（可选，如果不提供则返回该仓库所有联邦的状态）
     * @return 联邦仓库状态列表
     */
    fun getFederationRepositoryStatus(
        projectId: String,
        repoName: String,
        federationId: String? = null
    ): List<FederationRepositoryStatusInfo>

    /**
     * 获取联邦成员状态
     *
     * @param projectId 项目ID
     * @param repoName 仓库名称
     * @param federationId 联邦ID
     * @return 联邦成员状态列表
     */
    fun getFederationMemberStatus(
        projectId: String,
        repoName: String,
        federationId: String
    ): List<FederationMemberStatusInfo>

    /**
     * 刷新联邦成员状态
     * 主动检测所有成员的连接状态
     *
     * @param projectId 项目ID
     * @param repoName 仓库名称
     * @param federationId 联邦ID
     */
    fun refreshMemberStatus(
        projectId: String,
        repoName: String,
        federationId: String
    )
}


