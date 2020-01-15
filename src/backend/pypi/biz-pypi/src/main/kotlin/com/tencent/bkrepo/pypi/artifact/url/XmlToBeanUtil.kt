package com.tencent.bkrepo.pypi.artifact.url

import java.io.StringReader
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBException

object XmlToBeanUtil {
    //TODO
    fun convertXmlStrToObject(clazz: Class<*>?, xmlStr: String?): Any? {
        var xmlObject: Any? = null
        try {
            val context = JAXBContext.newInstance(clazz)
            // 进行将Xml转成对象的核心接口
            val unmarshaller = context.createUnmarshaller()
            val sr = StringReader(xmlStr)
            xmlObject = unmarshaller.unmarshal(sr)
        } catch (e: JAXBException) {
            e.printStackTrace()
        }
        return xmlObject
    }
}