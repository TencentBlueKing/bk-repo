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
 *      .limit(0, 50)
 *      .projectId("test")
 *      .repoName("generic-local")
 *      .and()
 *        .rule("path", "/data")
 *        .rule("folder", false)
 *        .rule("size", 1024, OperationType.GTE)
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
     * 设置分页查询条件，[current]代表当前页, 从0开始，[size]代表分页大小
     */
    fun limit(current: Int = 0, size: Int = 20): NodeQueryBuilder {
        require(current >= 0) { "page index must ge 0" }
        require(size > 0) { "page size must gt 0" }
        this.pageLimit = PageLimit(current, size)
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

    private fun createNestedRule(relation: Rule.NestedRule.RelationType): Rule.NestedRule {
        return Rule.NestedRule(mutableListOf(), relation)
    }
}
