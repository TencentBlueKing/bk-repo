package com.tencent.bkrepo.repository.pojo.query

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.repository.constant.SystemMetadata
import com.tencent.bkrepo.repository.pojo.stage.ArtifactStageEnum

/**
 * 节点自定义查询构造器
 *
 * 链式构造节点QueryModel
 * example:  查询/data目录下大于1024字节的文件
 * val queryModel = NodeQueryBuilder()
 *      .select("size", "name", "path")
 *      .sortByAsc("name")
 *      .page(1, 50)
 *      .projectId("test")
 *      .repoName("generic-local")
 *      .and()
 *        .path("/data")
 *        .size(1024, OperationType.GT)
 *        .excludeFolder()
 *      .build()
 */
class NodeQueryBuilder : AbstractQueryBuilder() {

    /**
     * 添加制品晋级状态规则
     *
     */
    fun stage(stage: ArtifactStageEnum, operation: OperationType = OperationType.EQ): AbstractQueryBuilder {
        return this.metadata(SystemMetadata.STAGE.key, stage.tag, operation)
    }

    /**
     * 添加文件名字段规则
     *
     * [value]为值，[operation]为查询操作类型，默认为EQ查询
     */
    fun name(value: String, operation: OperationType = OperationType.EQ): AbstractQueryBuilder {
        return this.rule(true, NAME_FILED, value, operation)
    }

    /**
     * 添加路径字段规则
     *
     * [value]为值，[operation]为查询操作类型，默认为EQ查询
     */
    fun path(value: String, operation: OperationType = OperationType.EQ): AbstractQueryBuilder {
        return this.rule(true, PATH_FILED, value, operation)
    }

    /**
     * 添加路径字段规则
     *
     * [value]为值，[operation]为查询操作类型，默认为EQ查询
     */
    fun fullPath(value: String, operation: OperationType = OperationType.EQ): AbstractQueryBuilder {
        return this.rule(true, FULL_PATH_FILED, value, operation)
    }

    /**
     * 添加文件大小字段规则
     *
     * [value]为值，[operation]为查询操作类型，默认为EQ查询
     */
    fun size(value: Long, operation: OperationType = OperationType.EQ): AbstractQueryBuilder {
        return this.rule(true, SIZE_FILED, value, operation)
    }

    /**
     * 排除目录
     */
    fun excludeFolder(): AbstractQueryBuilder {
        return this.rule(true, FOLDER_FILED, false, OperationType.EQ)
    }

    /**
     * 排除文件
     */
    fun excludeFile(): AbstractQueryBuilder {
        return this.rule(true, FOLDER_FILED, true, OperationType.EQ)
    }

    companion object {
        private const val SIZE_FILED = "size"
        private const val NAME_FILED = "name"
        private const val PATH_FILED = "path"
        private const val FULL_PATH_FILED = "fullPath"
        private const val FOLDER_FILED = "folder"
    }
}
