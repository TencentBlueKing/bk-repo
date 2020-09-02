package com.tencent.bkrepo.repository.pojo.node

import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort

/**
 * 节点自定义查询构造器
 *
 * 链式构造节点QueryModel
 * example:  查询/data目录下大于1024字节的文件
 * val queryModel = NodeQueryBuilder()
 *      .select("size", "name", "path")
 *      .sortByAsc("name")
 *      .page(0, 50)
 *      .projectId("test")
 *      .repoName("generic-local")
 *      .and()
 *        .path("/data")
 *        .size(1024, OperationType.GT)
 *        .excludeFolder()
 *      .build()
 */
class NodeQueryBuilder {
    private var projectId: String? = null
    private var repoNames: List<String> = listOf()

    private var fields: List<String>? = null
    private var sort: Sort? = null
    private var pageLimit: PageLimit = PageLimit()
    private var rootRule: Rule.NestedRule = createNestedRule(Rule.NestedRule.RelationType.AND)
    private var currentRule: Rule.NestedRule = rootRule

    /**
     * 构造QueryModel
     * 要求：project必须指定
     */
    fun build(): QueryModel {
        requireNotNull(projectId) { "ProjectId must be specific" }
        require(repoNames.isNotEmpty()) { "RepoName must be specific" }

        val projectQuery = Rule.QueryRule("projectId", projectId!!, OperationType.EQ)
        val repoQuery = if (repoNames.size == 1) {
            Rule.QueryRule("repoName", repoNames.first(), OperationType.EQ)
        } else {
            Rule.QueryRule("repoName", repoNames, OperationType.IN)
        }

        rootRule.rules.add(projectQuery)
        rootRule.rules.add(repoQuery)

        return QueryModel(
            select = fields,
            page = pageLimit,
            sort = sort,
            rule = rootRule
        )
    }

    /**
     * 设置查询字段[fields]
     */
    fun select(vararg fields: String): NodeQueryBuilder {
        if (fields.isNotEmpty()) {
            this.fields = fields.toList()
        }
        return this
    }

    /**
     * 按字段[fields]降序排序
     */
    fun sortByAsc(vararg fields: String): NodeQueryBuilder {
        return sort(Sort.Direction.ASC, *fields)
    }

    /**
     * 按字段[fields]升序排序
     */
    fun sortByDesc(vararg fields: String): NodeQueryBuilder {
        return sort(Sort.Direction.DESC, *fields)
    }

    /**
     * 按字段[fields]排序，排序方向为[direction]
     */
    fun sort(direction: Sort.Direction, vararg fields: String): NodeQueryBuilder {
        if (fields.isNotEmpty()) {
            this.sort = Sort(fields.toList(), direction)
        }
        return this
    }

    /**
     * 设置分页查询条件，[pageNumber]代表当前页, 从1开始，[pageSize]代表分页大小
     */
    fun page(pageNumber: Int, pageSize: Int): NodeQueryBuilder {
        require(pageNumber >= 0) { "page index must gte 0" }
        require(pageSize > 0) { "page size must gt 0" }
        this.pageLimit = PageLimit(pageNumber, pageSize)
        return this
    }

    /**
     * 设置项目id为[projectId]
     */
    fun projectId(projectId: String): NodeQueryBuilder {
        this.projectId = projectId
        return this
    }

    /**
     * 设置仓库名称为[repoName]
     */
    fun repoName(repoName: String): NodeQueryBuilder {
        this.repoNames = listOf(repoName)
        return this
    }

    /**
     * 设置多个仓库
     */
    fun repoNames(vararg repoNames: String): NodeQueryBuilder {
        this.repoNames = repoNames.toList()
        return this
    }

    /**
     * 添加and嵌套规则
     *
     * 执行后接下来添加的查询为and关系
     */
    fun and(): NodeQueryBuilder {
        val newRule = createNestedRule(Rule.NestedRule.RelationType.AND)
        currentRule.rules.add(newRule)
        currentRule = newRule
        return this
    }

    /**
     * 添加or嵌套规则
     *
     * 执行后接下来添加的查询为or关系
     */
    fun or(): NodeQueryBuilder {
        val newRule = createNestedRule(Rule.NestedRule.RelationType.OR)
        currentRule.rules.add(newRule)
        currentRule = newRule
        return this
    }

    /**
     * 添加字段规则
     *
     * [field]为字段名称，[value]为值，[operation]为查询操作类型，默认为EQ查询
     */
    fun rule(field: String, value: Any, operation: OperationType = OperationType.EQ): NodeQueryBuilder {
        return this.rule(true, field, value, operation)
    }

    /**
     * 根据条件添加字段规则，当[condition]为`true`时才添加
     *
     * [field]为字段名称，[value]为值，[operation]为查询操作类型，默认为EQ查询
     */
    fun rule(condition: Boolean, field: String, value: Any, operation: OperationType = OperationType.EQ): NodeQueryBuilder {
        if (condition) {
            val queryRule = Rule.QueryRule(field, value, operation)
            currentRule.rules.add(queryRule)
        }
        return this
    }

    /**
     * 添加元数据字段规则
     *
     * [key]为元数据名称，[value]为值，[operation]为查询操作类型，默认为EQ查询
     */
    fun metadata(key: String, value: Any, operation: OperationType = OperationType.EQ): NodeQueryBuilder {
        return this.rule(true, METADATA_PREFIX + key, value, operation)
    }

    /**
     * 添加文件名字段规则
     *
     * [value]为值，[operation]为查询操作类型，默认为EQ查询
     */
    fun name(value: String, operation: OperationType = OperationType.EQ): NodeQueryBuilder {
        return this.rule(true, NAME_FILED, value, operation)
    }

    /**
     * 添加路径字段规则
     *
     * [value]为值，[operation]为查询操作类型，默认为EQ查询
     */
    fun path(value: String, operation: OperationType = OperationType.EQ): NodeQueryBuilder {
        return this.rule(true, PATH_FILED, value, operation)
    }

    /**
     * 添加路径字段规则
     *
     * [value]为值，[operation]为查询操作类型，默认为EQ查询
     */
    fun fullPath(value: String, operation: OperationType = OperationType.EQ): NodeQueryBuilder {
        return this.rule(true, FULL_PATH_FILED, value, operation)
    }

    /**
     * 添加文件大小字段规则
     *
     * [value]为值，[operation]为查询操作类型，默认为EQ查询
     */
    fun size(value: Long, operation: OperationType = OperationType.EQ): NodeQueryBuilder {
        return this.rule(true, SIZE_FILED, value, operation)
    }

    /**
     * 排除目录
     */
    fun excludeFolder(): NodeQueryBuilder {
        return this.rule(true, FOLDER_FILED, false, OperationType.EQ)
    }

    /**
     * 排除文件
     */
    fun excludeFile(): NodeQueryBuilder {
        return this.rule(true, FOLDER_FILED, true, OperationType.EQ)
    }

    private fun createNestedRule(relation: Rule.NestedRule.RelationType): Rule.NestedRule {
        return Rule.NestedRule(mutableListOf(), relation)
    }

    companion object {
        private const val METADATA_PREFIX = "metadata."
        private const val SIZE_FILED = "size"
        private const val NAME_FILED = "name"
        private const val PATH_FILED = "path"
        private const val FULL_PATH_FILED = "fullPath"
        private const val FOLDER_FILED = "folder"
    }
}
