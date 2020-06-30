package com.tencent.bkrepo.common.quartz.util

import com.mongodb.client.model.Filters
import com.novemberain.quartz.mongodb.util.Keys.KEY_GROUP
import org.bson.BsonDocument
import org.bson.conversions.Bson
import org.quartz.impl.matchers.GroupMatcher
import org.quartz.impl.matchers.StringMatcher.StringOperatorName

object QueryHelper {
    fun matchingKeysConditionFor(matcher: GroupMatcher<*>): Bson {
        val compareToValue = matcher.compareToValue
        when (matcher.compareWithOperator) {
            StringOperatorName.EQUALS -> return Filters.eq(KEY_GROUP, compareToValue)
            StringOperatorName.STARTS_WITH -> return Filters.regex(KEY_GROUP, "^$compareToValue.*")
            StringOperatorName.ENDS_WITH -> return Filters.regex(KEY_GROUP, ".*$compareToValue$")
            StringOperatorName.CONTAINS -> return Filters.regex(KEY_GROUP, compareToValue)
        }
        return BsonDocument()
    }

    fun inGroups(groups: Collection<String>): Bson {
        return Filters.`in`(KEY_GROUP, groups)
    }
}
