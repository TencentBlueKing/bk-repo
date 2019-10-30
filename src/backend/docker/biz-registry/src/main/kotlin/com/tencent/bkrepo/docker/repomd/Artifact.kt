package com.tencent.bkrepo.docker.repomd

 class Artifact(path: String )  {

     var fielSha256 : String? = null
     var contentLength : Long = 0
     var path : String = ""

     init {
         this.path = path
     }

     fun sha256(fielSha256: String):Artifact {
         this.fielSha256 = fielSha256
         return this
     }

     fun contentLength(contentLength: Long):Artifact {
         this.contentLength = contentLength
         return this
     }

     fun path(path :String):Artifact{
         this.path = path
         return this
     }

     fun getSha256():String?{
         return  this.fielSha256
     }

     fun getLength():Long{
         return  this.contentLength
     }

     fun getArtifactPath():String {
         return  this.path
     }

     fun getRepoId() :String{
         return ""
     }
}
