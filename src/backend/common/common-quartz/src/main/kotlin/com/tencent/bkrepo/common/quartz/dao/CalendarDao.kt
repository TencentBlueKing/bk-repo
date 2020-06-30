package com.tencent.bkrepo.common.quartz.dao

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Projections
import com.tencent.bkrepo.common.quartz.util.SerialUtils
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.Binary
import org.quartz.Calendar
import org.quartz.JobPersistenceException

class CalendarDao(private val calendarCollection: MongoCollection<Document>) {

    fun createIndex() {
        calendarCollection.createIndex(
            Projections.include(CALENDAR_NAME),
            IndexOptions().unique(true)
        )
    }

    fun clear() {
        calendarCollection.deleteMany(Document())
    }

    fun getCount(): Long = calendarCollection.count()

    fun remove(name: String): Boolean {
        val searchObj: Bson = Filters.eq(CALENDAR_NAME, name)
        if (calendarCollection.count(searchObj) > 0) {
            calendarCollection.deleteMany(searchObj)
            return true
        }
        return false
    }

    @Throws(JobPersistenceException::class)
    fun retrieveCalendar(calName: String?): Calendar? {
        if (calName != null) {
            val searchObj = Filters.eq(CALENDAR_NAME, calName)
            val doc: Document? = calendarCollection.find(searchObj).first()
            if (doc != null) {
                val serializedCalendar = doc.get(CALENDAR_SERIALIZED_OBJECT, Binary::class.java)
                return SerialUtils.deserialize(serializedCalendar, Calendar::class.java)
            }
        }
        return null
    }

    @Throws(JobPersistenceException::class)
    fun store(name: String?, calendar: Calendar?) {
        val doc = Document(CALENDAR_NAME, name)
            .append(CALENDAR_SERIALIZED_OBJECT, SerialUtils.serialize(calendar))
        calendarCollection.insertOne(doc)
    }

    fun retrieveCalendarNames(): MutableList<String> {
        return calendarCollection
            .find()
            .projection(Projections.include(CALENDAR_NAME))
            .map { document -> document.getString(CALENDAR_NAME) }
            .into(mutableListOf())
    }

    companion object {
        const val CALENDAR_NAME: String = "name"
        const val CALENDAR_SERIALIZED_OBJECT: String = "serializedObject"
    }

}