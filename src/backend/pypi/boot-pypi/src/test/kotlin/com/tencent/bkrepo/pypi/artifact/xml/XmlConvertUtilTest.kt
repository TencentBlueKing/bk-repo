package com.tencent.bkrepo.pypi.artifact.xml

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class XmlConvertUtilTest {

    //pip search response xml content
    private val responseXmlStr = "<methodResponse>\n" +
            "  <params>\n" +
            "    <param>\n" +
            "      <value>\n" +
            "        <array>\n" +
            "          <data>\n" +
            "            <value>\n" +
            "              <struct>\n" +
            "                <member>\n" +
            "                  <name>_pypi_ordering</name>\n" +
            "                  <value>\n" +
            "                    <int>1</int>\n" +
            "                  </value>\n" +
            "                </member>\n" +
            "                <member>\n" +
            "                  <name>version</name>\n" +
            "                  <value>\n" +
            "                    <string>0.0.3</string>\n" +
            "                  </value>\n" +
            "                </member>\n" +
            "                <member>\n" +
            "                  <name>name</name>\n" +
            "                  <value>\n" +
            "                    <string>http3test</string>\n" +
            "                  </value>\n" +
            "                </member>\n" +
            "                <member>\n" +
            "                  <name>summary</name>\n" +
            "                  <value>\n" +
            "                    <string>A small example package</string>\n" +
            "                  </value>\n" +
            "                </member>\n" +
            "              </struct>\n" +
            "            </value>\n" +
            "            <value>\n" +
            "              <struct>\n" +
            "                <member>\n" +
            "                  <name>_pypi_ordering</name>\n" +
            "                  <value>\n" +
            "                    <int>0</int>\n" +
            "                  </value>\n" +
            "                </member>\n" +
            "                <member>\n" +
            "                  <name>version</name>\n" +
            "                  <value>\n" +
            "                    <string>0.0.1</string>\n" +
            "                  </value>\n" +
            "                </member>\n" +
            "                <member>\n" +
            "                  <name>name</name>\n" +
            "                  <value>\n" +
            "                    <string>http3test</string>\n" +
            "                  </value>\n" +
            "                </member>\n" +
            "                <member>\n" +
            "                  <name>summary</name>\n" +
            "                  <value>\n" +
            "                    <string>A small example package</string>\n" +
            "                  </value>\n" +
            "                </member>\n" +
            "              </struct>\n" +
            "            </value>\n" +
            "          </data>\n" +
            "        </array>\n" +
            "      </value>\n" +
            "    </param>\n" +
            "  </params>\n" +
            "</methodResponse>"

    //pip search  request xml content
    private val requestXml = "<?xml version='1.0'?>\n" +
            "<methodCall>\n" +
            "<methodName>search</methodName>\n" +
            "<params>\n" +
            "<param>\n" +
            "<value><struct>\n" +
            "<member>\n" +
            "<name>name</name>\n" +
            "<value><array><data>\n" +
            "<value><string>http3test</string></value>\n" +
            "</data></array></value>\n" +
            "</member>\n" +
            "<member>\n" +
            "<name>summary</name>\n" +
            "<value><array><data>\n" +
            "<value><string>http3test</string></value>\n" +
            "</data></array></value>\n" +
            "</member>\n" +
            "</struct></value>\n" +
            "</param>\n" +
            "<param>\n" +
            "<value><string>or</string></value>\n" +
            "</param>\n" +
            "</params>\n" +
            "</methodCall>"

    @Test
    fun xml2MethodCallTest() {
        val methodCall = XmlConvertUtil.xml2MethodCall(requestXml)
        Assertions.assertNotNull(methodCall)
    }

    @Test
    fun xml2MethodResponseTest() {
        val methodResponse = XmlConvertUtil.xml2MethodResponse(responseXmlStr)
        Assertions.assertNotNull(methodResponse)
    }

}