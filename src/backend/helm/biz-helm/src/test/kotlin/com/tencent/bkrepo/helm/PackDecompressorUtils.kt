package com.tencent.bkrepo.helm

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringUtils
import java.io.*


object PackDecompressor {
	private const val BUFFER_SIZE = 2048

	@Throws(Exception::class)
	@JvmStatic
	fun main(args: Array<String>) {
		unTarGZ("/Users/chencan/Downloads/mychart-0.1.0.tgz", "/Users/chencan/Downloads/mychart")
	}

	@Throws(Exception::class)
	fun unTarGZ(file: String, destDir: String) {
		val tarFile = File(file)
		unTarGZ(tarFile, destDir)
	}

	@Throws(Exception::class)
	fun unTarGZ(tarFile: File, destDir: String) {
		var destDir = destDir
		if (StringUtils.isBlank(destDir)) {
			destDir = tarFile.parent
		}
		destDir = if (destDir.endsWith(File.separator)) destDir else destDir + File.separator
		unTar(GzipCompressorInputStream(FileInputStream(tarFile)), destDir)
	}

	@Throws(Exception::class)
	private fun unTar(inputStream: InputStream, destDir: String) {

		val tarIn = TarArchiveInputStream(inputStream, BUFFER_SIZE)
		var entry: TarArchiveEntry? = null
		try {
			while (({entry = tarIn.nextTarEntry;entry}()) != null) {

				if (entry!!.isDirectory) {//是目录
					createDirectory(destDir, entry!!.name)//创建空目录
				} else {//是文件
					val tmpFile = File(destDir + File.separator + entry!!.name)
					createDirectory(tmpFile.parent + File.separator, null)//创建输出目录
					var out: OutputStream? = null
					try {
						out = FileOutputStream(tmpFile)
						var length = 0
						val b = ByteArray(2048)
						while (({length = tarIn.read(b);length}()) != -1) {
							out!!.write(b, 0, length)
						}
					} finally {
						IOUtils.closeQuietly(out)
					}
				}
			}
		} catch (e: Exception) {
			e.printStackTrace()
			throw e
		} finally {
			IOUtils.closeQuietly(tarIn)
		}
	}

	fun createDirectory(outputDir: String, subDir: String?) {
		var file = File(outputDir)
		if (!(subDir == null || subDir.trim { it <= ' ' } == "")) {//子目录不为空
			file = File(outputDir + File.separator + subDir)
		}
		if (!file.exists()) {
			file.mkdirs()
		}
	}
}