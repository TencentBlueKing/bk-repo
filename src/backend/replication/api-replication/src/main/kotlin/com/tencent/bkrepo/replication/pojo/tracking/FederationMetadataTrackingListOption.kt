package com.tencent.bkrepo.replication.pojo.tracking

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 联邦元数据跟踪记录查询参数
 */
@Schema(title = "联邦元数据跟踪记录查询参数")
data class FederationMetadataTrackingListOption(
    @get:Schema(title = "当前页")
    val pageNumber: Int = DEFAULT_PAGE_NUMBER,
    @get:Schema(title = "分页大小")
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    @get:Schema(title = "任务key")
    val taskKey: String? = null,
    @get:Schema(title = "远程集群id")
    val remoteClusterId: String? = null,
    @get:Schema(title = "项目ID")
    val projectId: String? = null,
    @get:Schema(title = "本地仓库名称")
    val localRepoName: String? = null,
    @get:Schema(title = "远程项目ID")
    val remoteProjectId: String? = null,
    @get:Schema(title = "远程仓库名称")
    val remoteRepoName: String? = null,
    @get:Schema(title = "是否正在重试")
    val retrying: Boolean? = null,
    @get:Schema(title = "最大重试次数（用于筛选失败记录）")
    val maxRetryCount: Int? = null,
    @get:Schema(title = "排序字段", allowableValues = ["createdDate", "lastModifiedDate", "retryCount"])
    val sortField: String? = "lastModifiedDate",
    @get:Schema(title = "排序方向", allowableValues = ["ASC", "DESC"])
    val sortDirection: String? = "DESC"
)

