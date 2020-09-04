package com.tencent.bkrepo.common.query

import com.mongodb.BasicDBList
import com.tencent.bkrepo.common.query.builder.MongoQueryInterpreter
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import org.bson.Document
import org.junit.jupiter.api.Test

class MongoQueryInterpreterTest {

    @Test
    fun buildTest() {
        val projectId = Rule.QueryRule("projectId", "1")
        val repoName = Rule.QueryRule("repoName", "repoName")
        val path = Rule.QueryRule("path", "/a/b/c")
        val rule1 = Rule.NestedRule(mutableListOf(path, projectId), Rule.NestedRule.RelationType.AND)

        val rule2 = Rule.NestedRule(mutableListOf(repoName, rule1), Rule.NestedRule.RelationType.AND)

        val queryModel = QueryModel(
            page = PageLimit(0, 10),
            sort = Sort(listOf("name"), Sort.Direction.ASC),
            select = mutableListOf("projectId", "repoName", "fullPath", "metadata"),
            rule = rule2
        )

        val builder = MongoQueryInterpreter()
        val query = builder.interpret(queryModel)
        println(query)
        println(query.queryObject)

        println(findProjectId(query.queryObject))
    }

    private fun findProjectId(document: Document): Any? {
        for ((key, value) in document) {
            if (key == "projectId") return value
            if (key == "\$and") {
                for (element in value as BasicDBList) {
                    findProjectId(element as Document)?.let { return it }
                }
            }
        }
        return null
    }
}
