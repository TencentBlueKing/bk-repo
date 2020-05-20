package com.tencent.bkrepo.pypi.util

import org.jsoup.Jsoup
import org.jsoup.select.Elements

object JsoupUtil {

    /**
     * @param limitPackages 限制packages 数量
     * @return
     */
    fun String.htmlHrefs(limitPackages: Int): Elements {
        val document = Jsoup.connect(this).get()
        val packages = document.body().select("a[href]")
        limitPackages.takeIf { it == -1 }?.apply {
            return packages
        }
        val limitElements = Elements(limitPackages)
        var packMark = 0
        for (e in packages) {
            limitElements.add(e)
            ++packMark
            if (packMark > limitPackages) {
                break
            }
        }
        return limitElements
    }

    fun String.htmlHrefs(): Elements {
        val document = Jsoup.connect(this).get()
        return document.body().select("a[href]")
    }



    fun String.sumTasks(elements: Elements): Int {
        var sum = 0
        for (e in elements) {
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