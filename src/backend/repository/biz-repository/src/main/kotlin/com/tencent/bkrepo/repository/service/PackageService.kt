package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import com.tencent.bkrepo.repository.pojo.packages.request.PackageVersionCreateRequest

/**
 * 包服务类接口
 */
interface PackageService {

    /**
     * 根据包名查询包信息
     *
     * @param projectId 项目id
     * @param repoName 仓库名称
     * @param packageKey 包唯一标识
     */
    fun findPackageByKey(
        projectId: String,
        repoName: String,
        packageKey: String
    ): PackageSummary?

    /**
     * 查询版本信息
     *
     * @param projectId 项目id
     * @param repoName 仓库名称
     * @param packageKey 包唯一标识
     * @param versionName 版本名称
     */
    fun findVersionByName(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String
    ): PackageVersion?

    /**
     * 分页查询包列表, 根据packageName模糊搜索
     *
     * @param projectId 项目id
     * @param repoName 仓库id
     * @param packageName 包名称
     * @param pageNumber 页码
     * @param pageSize 每页数量
     */
    fun listPackagePageByName(
        projectId: String,
        repoName: String,
        packageName: String? = null,
        pageNumber: Int,
        pageSize: Int
    ): Page<PackageSummary>

    /**
     * 查询版本列表
     *
     * @param projectId 项目id
     * @param repoName 仓库id
     * @param packageKey 包唯一标识
     * @param pageNumber 页码
     * @param pageSize 每页数量
     */
    fun listVersionPage(
        projectId: String,
        repoName: String,
        packageKey: String,
        pageNumber: Int,
        pageSize: Int
    ): Page<PackageVersion>

    /**
     * 创建包版本
     * 如果包不存在，会自动创建包
     *
     * @param request 包版本创建请求
     */
    fun createPackageVersion(request: PackageVersionCreateRequest)

    /**
     * 删除包
     *
     * @param projectId 项目id
     * @param repoName 项目id
     * @param packageKey 包唯一标识
     */
    fun deletePackage(
        projectId: String,
        repoName: String,
        packageKey: String
    )

    /**
     * 删除包版本
     *
     * @param projectId 项目id
     * @param repoName 项目id
     * @param packageKey 包唯一标识
     * @param versionName 版本名称
     */
    fun deleteVersion(
        projectId: String,
        repoName: String,
        packageKey: String,
        versionName: String
    )

    /**
     * 根据[queryModel]搜索包
     */
    fun search(queryModel: QueryModel): Page<PackageSummary>
}