package com.tencent.bkrepo.common.storage.innercos.http

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import okhttp3.Response

abstract class HttpResponseHandler<T> {

    abstract fun handle(response: Response): T
    open fun handle404(): T? = null
    open fun keepConnection(): Boolean = false

    companion object {
        private val xmlMapper: XmlMapper = XmlMapper()

        fun readXmlValue(response: Response): Map<*, *> {
            return xmlMapper.readValue(response.body()?.string(), Map::class.java)
        }
    }
}
