package com.tencent.bkrepo.pypi.util

import com.tencent.bkrepo.pypi.util.JsoupUtil.htmlHrefs
import org.jsoup.Jsoup
import org.jsoup.select.Elements

object JsoupUtil {
    fun String.htmlHrefs(): Elements {
        val document = Jsoup.connect(this).get()
        return document.body().select("a[href]")
    }

    fun String.sumTasks(): Int {
        var sum = 0
        val document = Jsoup.connect(this).get()
        val packages = document.body().select("a[href]")
        for (e in packages) {
            e.text()?.let { packageName ->
                "$this/$packageName".htmlHrefs().let { filenodes ->
                    for (filenode in filenodes) {
                        sum++
                    }
                }
            }
        }
        return sum
    }
}