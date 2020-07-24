package com.tencent.bkrepo.docker.manifest

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ManifestTypeTest {

    @Test
    @DisplayName("测试通过content type获取manifest type")
    fun manifestTypeTest() {
        var contentType = "application/vnd.docker.distribution.manifest.v2+json"
        val result = ManifestType.from(contentType)
        Assertions.assertEquals(result, ManifestType.Schema2)
    }

    @Test
    @DisplayName("测试通过manifest文件获取type")
    fun manifestFromByteArrayTest() {
        val data = """{
   "schemaVersion": 2,
   "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
   "config": {
      "mediaType": "application/vnd.docker.container.image.v1+json",
      "size": 7645,
      "digest": "sha256:e2b047b17a138636a6ba9abd71c6e2e99ff38a5110177d2a34557eff61e0b040"
   },
   "layers": [
      {
         "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
         "size": 27098756,
         "digest": "sha256:afb6ec6fdc1c3ba04f7a56db32c5ff5ff38962dc4cd0ffdef5beaa0ce2eb77e2"
      },
      {
         "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
         "size": 26201443,
         "digest": "sha256:2e231683bfde7f6cdb860dcaf855c8aaf8a4cdb83ab8dd345ab35e8d5a2e421a"
      },
      {
         "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
         "size": 202,
         "digest": "sha256:511e2efefada0b20168024e58d73b3c5dcaa71383f77461c4be74d742838f5df"
      },
      {
         "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
         "size": 635,
         "digest": "sha256:e8fd0ec105c9a0ba769b2357195c0842fddf670c55f9e6a4a5f55d6fa61311d5"
      },
      {
         "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
         "size": 1256153,
         "digest": "sha256:97357440e4e4b86c59294355fd3747ec274866b3028f9962d16ee131e8bdb0db"
      }
   ]
}"""
        val byte = data.toByteArray()
        val result = ManifestType.from(byte)
        Assertions.assertEquals(result, ManifestType.Schema2)
    }
}