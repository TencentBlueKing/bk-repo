package com.tencent.bkrepo.common.query.serializer

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.exception.QueryModelException
import com.tencent.bkrepo.common.query.model.Rule
import java.io.IOException

/**
 * 规则模型 反序列化类
 */
class RuleDeserializer : JsonDeserializer<Rule>() {

    @Throws(IOException::class, JsonProcessingException::class)
    override fun deserialize(parser: JsonParser, context: DeserializationContext): Rule {
        val mapper = parser.codec as ObjectMapper
        val node = mapper.readTree<JsonNode>(parser)
        try {
            return if (node["relation"] != null) {
                val relation = Rule.NestedRule.RelationType.lookup(node["relation"].asText())
                val rules = mapper.readValue<MutableList<Rule>>(node["rules"].toString())

                Rule.NestedRule(rules, relation)
            } else {
                val operation = OperationType.lookup(node["operation"].asText())
                val field = node["field"].asText()

                val value = if (operation.valueType != Void::class) {
                    mapper.readValue(node["value"].toString(), operation.valueType.java)
                } else StringPool.EMPTY

                Rule.QueryRule(field, value, operation)
            }
        } catch (exception: IOException) {
            throw QueryModelException("Failed to resolve rule.", exception)
        }
    }
}
