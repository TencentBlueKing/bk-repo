package com.tencent.bkrepo.common.quartz.util

import org.apache.commons.codec.binary.Base64
import org.bson.types.Binary
import org.quartz.Calendar
import org.quartz.JobDataMap
import org.quartz.JobPersistenceException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.NotSerializableException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

object SerialUtils {
    private const val SERIALIZE_MESSAGE_FORMAT = "Unable to serialize JobDataMap for insertion into " +
        "database because the value of property '%s' " +
        "is not serializable: %s"

    @Throws(JobPersistenceException::class)
    fun serialize(calendar: Calendar?): Any {
        val byteStream = ByteArrayOutputStream()
        return try {
            val objectStream = ObjectOutputStream(byteStream)
            objectStream.writeObject(calendar)
            objectStream.close()
            byteStream.toByteArray()
        } catch (e: IOException) {
            throw JobPersistenceException("Could not serialize Calendar.", e)
        }
    }

    @Throws(JobPersistenceException::class)
    fun <T> deserialize(serialized: Binary, clazz: Class<T>): T {
        val byteStream = ByteArrayInputStream(serialized.data)
        try {
            val objectStream = ObjectInputStream(byteStream)
            val objectData = objectStream.readObject()
            objectStream.close()
            if (clazz.isInstance(objectData)) {
                return objectData as T
            }
            throw JobPersistenceException("Deserialized object is not of the desired type")
        } catch (e: IOException) {
            throw JobPersistenceException("Could not deserialize.", e)
        } catch (e: ClassNotFoundException) {
            throw JobPersistenceException("Could not deserialize.", e)
        }
    }

    @Throws(IOException::class)
    fun serialize(jobDataMap: JobDataMap): String {
        return try {
            val bytes = stringMapToBytes(jobDataMap.wrappedMap)
            Base64.encodeBase64String(bytes)
        } catch (e: NotSerializableException) {
            rethrowEnhanced(jobDataMap, e)
        }
    }

    @Throws(IOException::class)
    fun deserialize(jobDataMap: JobDataMap?, clob: String?): Map<String, *> {
        try {
            val bytes = Base64.decodeBase64(clob)
            return stringMapFromBytes(bytes)
        } catch (e: NotSerializableException) {
            rethrowEnhanced(jobDataMap, e)
        } catch (e: ClassNotFoundException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
        return emptyMap<String, Any>()
    }

    @Throws(IOException::class)
    private fun stringMapToBytes(any: Any): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
        objectOutputStream.writeObject(any)
        objectOutputStream.flush()
        return byteArrayOutputStream.toByteArray()
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun stringMapFromBytes(bytes: ByteArray): Map<String, *> {
        val byteArrayOutputStream = ByteArrayInputStream(bytes)
        val objectInputStream = ObjectInputStream(byteArrayOutputStream)
        val map = objectInputStream.readObject() as Map<String, *>
        objectInputStream.close()
        return map
    }

    @Throws(NotSerializableException::class)
    private fun rethrowEnhanced(jobDataMap: JobDataMap?, e: NotSerializableException): String {
        val key = getKeyOfNonSerializableStringMapEntry(jobDataMap?.wrappedMap)
        throw NotSerializableException(String.format(SERIALIZE_MESSAGE_FORMAT, key, e.message))
    }

    private fun getKeyOfNonSerializableStringMapEntry(data: Map<String, *>?): String? {
        data?.forEach { (key, value) ->
            val byteArrayOutputStream = ByteArrayOutputStream()
            try {
                val out = ObjectOutputStream(byteArrayOutputStream)
                out.writeObject(value)
                out.flush()
            } catch (e: IOException) {
                return key
            }
        }
        return null
    }
}
