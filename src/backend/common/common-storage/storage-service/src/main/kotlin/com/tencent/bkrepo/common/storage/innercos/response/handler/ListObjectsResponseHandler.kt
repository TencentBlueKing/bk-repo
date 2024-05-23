package com.tencent.bkrepo.common.storage.innercos.response.handler

import com.tencent.bkrepo.common.storage.innercos.client.CosClient
import com.tencent.bkrepo.common.storage.innercos.http.HttpResponseHandler
import com.tencent.bkrepo.common.storage.innercos.request.ListObjectsRequest
import com.tencent.bkrepo.common.storage.innercos.response.ListObjectsResponse
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.util.stream.Stream
import kotlin.streams.asStream

class ListObjectsResponseHandler(val client: CosClient, val req: ListObjectsRequest) :
    HttpResponseHandler<Stream<String>>() {
    override fun handle(response: Response): Stream<String> {
        val listRes = readXmlValue<ListObjectsResponse>(response)
        return Itr(listRes, client, req).asSequence().asStream()
    }

    private class Itr(var response: ListObjectsResponse, val client: CosClient, val req: ListObjectsRequest) :
        Iterator<String> {

        private var it = response.contents.iterator()
        private var nextMarker = response.nextMarker
        override fun hasNext(): Boolean {
            if (!it.hasNext() && nextMarker.isNotEmpty()) {
                load()
            }
            return it.hasNext()
        }

        override fun next(): String {
            return it.next().key
        }

        private fun load() {
            val listObjectsRequest = ListObjectsRequest(prefix = req.prefix, marker = response.nextMarker, req.maxKeys)
            response = client.listObjects0(listObjectsRequest)
            val size = response.contents.size
            if (logger.isDebugEnabled && size > 0) {
                logger.debug("load $size objects.")
            }
            it = response.contents.iterator()
            nextMarker = response.nextMarker
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ListObjectsResponseHandler::class.java)
    }
}
