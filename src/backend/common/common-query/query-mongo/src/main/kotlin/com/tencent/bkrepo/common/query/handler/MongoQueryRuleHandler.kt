package com.tencent.bkrepo.common.query.handler

import com.tencent.bkrepo.common.query.model.Rule
import org.springframework.data.mongodb.core.query.Criteria

/**
 *
 * @author: carrypan
 * @date: 2019/11/15
 */
interface MongoQueryRuleHandler {

    fun match(rule: Rule.QueryRule): Boolean

    fun handle(rule: Rule.QueryRule): Criteria
}
