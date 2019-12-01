package com.tencent.bkrepo.docker.v2.helpers

import org.apache.commons.lang.StringUtils
import java.util.*


class DockerCatalogTagsSlicer {
    companion object {

        fun sliceCatalog(elementsHolder: DockerPaginationElementsHolder, maxEntries: Int, lastEntry: String) {
            if (!elementsHolder.elements.isEmpty()) {
                val fromElement = calcFromElement(elementsHolder, lastEntry)
                if (StringUtils.isBlank(fromElement)) {
                    elementsHolder.elements = TreeSet()
                } else {
                    val toElement = calcToElement(elementsHolder, fromElement, maxEntries)
                    if (!StringUtils.equals(fromElement, toElement)) {
                        if (!StringUtils.equals(toElement, elementsHolder.elements.last() as String)) {
                            if (StringUtils.isBlank(toElement)) {
                                elementsHolder.elements = TreeSet()
                            } else {
                                elementsHolder.hasMoreElements = !StringUtils.equals(elementsHolder.elements.last() as String, toElement)
                                elementsHolder.elements = elementsHolder.elements.subSet(fromElement, true, toElement, true) as TreeSet<String>
                            }
                        } else if (!StringUtils.equals(fromElement, elementsHolder.elements.first() as String)) {
                            elementsHolder.elements = elementsHolder.elements.subSet(fromElement, true, toElement, true) as TreeSet<String>
                        }
                    } else {
                        elementsHolder.hasMoreElements = !StringUtils.equals(elementsHolder.elements.last() as String, toElement)
                        elementsHolder.elements = elementsHolder.elements.subSet(fromElement, true, toElement, true) as TreeSet<String>
                    }

                }
            }
        }

        private fun calcFromElement(elementsHolder: DockerPaginationElementsHolder, lastEntry: String): String {
            var fromElement = elementsHolder.elements.first() as String
            if (StringUtils.isNotBlank(lastEntry)) {
                fromElement = elementsHolder.elements.higher(lastEntry) as String
            }

            return fromElement
        }

        private fun calcToElement(elementsHolder: DockerPaginationElementsHolder, fromElement: String, maxEntries: Int): String {
            var toElement = elementsHolder.elements.last() as String
            if (maxEntries > 0) {
                val repos = elementsHolder.elements.tailSet(fromElement)
                var repoIndex = 1
                val iter = repos.iterator()

                while (iter.hasNext()) {
                    val repo = iter.next() as String
                    if (repoIndex++ == maxEntries) {
                        toElement = repo
                        break
                    }
                }
            }

            return toElement
        }
    }
}