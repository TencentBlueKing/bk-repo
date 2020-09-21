package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.pojo.packages.PackageInfo

/**
 * 包服务类接口
 */
interface PackageService {

    /**
     * 查询某个版本包
     *
     * @param projectId 项目id
     * @param repoName 项目id
     * @param packageName 包名称
     * @param packageVersion 包版本
     */
    fun findVersion(
        projectId: String,
        repoName: String,
        packageName: String,
        packageVersion: String
    ): PackageInfo

    /**
     * 删除某个包
     *
     * @param projectId 项目id
     * @param repoName 项目id
     * @param packageName 包名称
     */
    fun deletePackage(
        projectId: String,
        repoName: String,
        packageName: String
    ): PackageInfo

    /**
     * 删除某个版本
     *
     * @param projectId 项目id
     * @param repoName 项目id
     * @param packageName 包名称
     * @param packageVersion 包版本
     */
    fun deleteVersion(
        projectId: String,
        repoName: String,
        packageName: String,
        packageVersion: String
    ): PackageInfo

    /**
     * 分页查询包列表
     *
     * @param projectId 项目id
     * @param repoName 项目id
     * @param packageName 包名称
     * @param pageNumber 页码
     * @param pageSize 每页数量
     */
    fun listPackagePage(
        projectId: String,
        repoName: String,
        packageName: String?,
        pageNumber: Int,
        pageSize: Int
    ): Page<PackageInfo>

    /**
     * 根据[queryModel]自定义查询包列表
     */
    fun query(queryModel: QueryModel): Page<PackageInfo>

}