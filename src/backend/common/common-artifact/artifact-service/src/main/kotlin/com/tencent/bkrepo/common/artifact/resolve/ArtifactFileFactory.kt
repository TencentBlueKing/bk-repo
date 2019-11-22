package com.tencent.bkrepo.common.artifact.resolve

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import java.io.File
import org.apache.commons.io.FileCleaningTracker

/**
 * ArtifactFileItem工厂方法
 *
 * @author: carrypan
 * @date: 2019/10/30
 */
class ArtifactFileFactory(
    private val sizeThreshold: Int = DEFAULT_SIZE_THRESHOLD,
    private val directory: File = File(System.getProperty("java.io.tmpdir")),
    private val tracker: FileCleaningTracker? = null
) {

    fun build(): ArtifactFile {
        val file = ArtifactFile(directory, sizeThreshold)
        tracker?.track(file.getTempFile(), file)
        return file
    }

    companion object {
        /**
         * The default threshold above which uploads will be stored on disk.
         */
        private const val DEFAULT_SIZE_THRESHOLD = 1024 * 1024
    }
}
