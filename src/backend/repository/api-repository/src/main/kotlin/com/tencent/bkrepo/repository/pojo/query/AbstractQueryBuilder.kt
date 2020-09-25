package com.tencent.bkrepo.repository.pojo.query

import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.repository.constant.METADATA_PREFIX

abstract class AbstractQueryBuilder {
    private var projectId: String? = null
    private var repoNames: List<String> = listOf()
    private var repoType: RepositoryType? = null

    private var fields: List<String>? = null
    private var sort: Sort? = null
    private var pageLimit: PageLimit = PageLimit()
    private var rootRule: Rule.NestedRule = createNestedRule(Rule.NestedRule.RelationType.AND)
    private var currentRule: Rule.NestedRule = rootRule

    /**
     * 设置查询字段[fields]
     */
    fun select(vararg fields: String): AbstractQueryBuilder {
        if (fields.isNotEmpty()) {
            this.fields = fields.toList()
        }
        return this
    }

    /**
     * 按字段[fields]降序排序
     */
    fun sortByAsc(vararg fields: String): AbstractQueryBuilder {
        return sort(Sort.Direction.ASC, *fields)
    }

    /**
     * 按字段[fields]升序排序
     */
    fun sortByDesc(vararg fields: String): AbstractQueryBuilder {
        return sort(Sort.Direction.DESC, *fields)
    }

    /**
     * 按字段[fields]排序，排序方向为[direction]
     */
    fun sort(direction: Sort.Direction, vararg fields: String): AbstractQueryBuilder {
        if (fields.isNotEmpty()) {
            this.sort = Sort(fields.toList(), direction)
        }
        return this
    }

    /**
     * 设置分页查询条件，[pageNumber]代表当前页, 从1开始，[pageSize]代表分页大小
     */
    fun page(pageNumber: Int, pageSize: Int): AbstractQueryBuilder {
        require(pageNumber >= 0) { "page index must gte 0" }
        require(pageSize > 0) { "page size must gt 0" }
        this.pageLimit = PageLimit(pageNumber, pageSize)
        return this
    }

    /**
     * 设置项目id为[projectId]
     */
    fun projectId(projectId: String): AbstractQueryBuilder {
        this.projectId = projectId
        return this
    }

    /**
     * 设置仓库名称为[repoName]
     */
    fun repoName(repoName: String): AbstractQueryBuilder {
        this.repoNames = listOf(repoName)
        return this
    }

    /**
     * 设置多个仓库
     */
    fun repoNames(vararg repoNames: String): AbstractQueryBuilder {
        this.repoNames = repoNames.toList()
        return this
    }

    /**
     * 设置仓库类型为[repoType]
     */
    fun repoType(repoType: RepositoryType): AbstractQueryBuilder {
        this.repoType = repoType
        return this
    }

    /**
     * 添加and嵌套规则
     *
     * 执行后接下来添加的查询为and关系
     */
    fun and(): AbstractQueryBuilder {
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
    fun or(): AbstractQueryBuilder {
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
    fun rule(field: String, value: Any, operation: OperationType = OperationType.EQ): AbstractQueryBuilder {
        return this.rule(true, field, value, operation)
    }

    /**
     * 根据条件添加字段规则，当[condition]为`true`时才添加
     *
     * [field]为字段名称，[value]为值，[operation]为查询操作类型，默认为EQ查询
     */
    fun rule(condition: Boolean, field: String, value: Any, operation: OperationType = OperationType.EQ): AbstractQueryBuilder {
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
    fun metadata(key: String, value: Any, operation: OperationType = OperationType.EQ): AbstractQueryBuilder {
        return this.rule(true, METADATA_PREFIX + key, value, operation)
    }

    /**
     * 构造QueryModel
     * 要求：project必须指定
     */
    fun build(): QueryModel {
        requireNotNull(projectId) { "ProjectId must be specific" }

        val projectQuery = projectId?.let { Rule.QueryRule("projectId", projectId!!, OperationType.EQ) }
        val repoTypeQuery = repoType?.let { Rule.QueryRule("repoType", repoType!!.name, OperationType.EQ) }
        val repoNameQuery = when {
            repoNames.size == 1 -> Rule.QueryRule("repoName", repoNames.first(), OperationType.EQ)
            repoNames.size > 1 -> Rule.QueryRule("repoName", repoNames, OperationType.IN)
            else -> null
        }
        projectQuery?.let { rootRule.rules.add(it) }
        repoTypeQuery?.let { rootRule.rules.add(it) }
        repoNameQuery?.let { rootRule.rules.add(it) }

        return QueryModel(
            select = fields,
            page = pageLimit,
            sort = sort,
            rule = rootRule
        )
    }

    private fun createNestedRule(relation: Rule.NestedRule.RelationType): Rule.NestedRule {
        return Rule.NestedRule(mutableListOf(), relation)
    }
}