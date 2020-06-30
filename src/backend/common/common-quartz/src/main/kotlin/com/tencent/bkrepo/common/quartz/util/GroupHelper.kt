package com.tencent.bkrepo.common.quartz.util

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.novemberain.quartz.mongodb.util.Keys.KEY_GROUP
import org.bson.Document
import org.quartz.impl.matchers.GroupMatcher

open class GroupHelper(
    private val collection: MongoCollection<Document>
) {
    fun groupsThatMatch(matcher: GroupMatcher<*>): MutableSet<String> {
        val filter = QueryHelper.matchingKeysConditionFor(matcher)
        return collection
            .distinct(KEY_GROUP, String::class.java)
            .filter(filter)
            .toSortedSet()
    }

    fun inGroupsThatMatch(matcher: GroupMatcher<*>): MutableList<Document> {
        return collection
            .find(Filters.`in`(KEY_GROUP, groupsThatMatch(matcher)))
            .toMutableList()
    }

    fun allGroups(): MutableSet<String> {
        return collection
            .distinct(KEY_GROUP, String::class.java)
            .toMutableSet()
    }
}
