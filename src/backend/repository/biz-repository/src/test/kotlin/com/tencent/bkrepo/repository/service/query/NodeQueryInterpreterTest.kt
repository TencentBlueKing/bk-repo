package com.tencent.bkrepo.repository.service.query

import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import org.junit.jupiter.api.Test

class NodeQueryInterpreterTest {

    @Test
    fun testQueryWithMetadata() {
        val projectId = Rule.QueryRule("projectId", "1")
        val repoName = Rule.QueryRule("repoName", "repoName")
        val metadata = Rule.QueryRule("metadata.key", "value")
        val rule = Rule.NestedRule(mutableListOf(projectId, repoName, metadata), Rule.NestedRule.RelationType.AND)
        val queryModel = QueryModel(
            page = PageLimit(1, 10),
            sort = Sort(listOf("name"), Sort.Direction.ASC),
            select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
            rule = rule
        )
        val interpreter = NodeQueryInterpreter()
        val query = interpreter.interpret(queryModel)
        println(query)
    }

    @Test
    fun testQueryWithStageTag() {
        val projectId = Rule.QueryRule("projectId", "1")
        val repoName = Rule.QueryRule("repoName", "repoName")
        val metadata = Rule.QueryRule("metadata.key", "value")
        val stageTag = Rule.QueryRule("stageTag", "@release")
        val rule = Rule.NestedRule(mutableListOf(projectId, repoName, metadata, stageTag), Rule.NestedRule.RelationType.AND)
        val queryModel = QueryModel(
            page = PageLimit(1, 10),
            sort = Sort(listOf("name"), Sort.Direction.ASC),
            select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
            rule = rule
        )
        val interpreter = NodeQueryInterpreter()
        val query = interpreter.interpret(queryModel)
        println(query)
    }
}