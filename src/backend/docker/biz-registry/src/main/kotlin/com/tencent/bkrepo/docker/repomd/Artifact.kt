package com.tencent.bkrepo.docker.repomd

 class Artifact(contentLength: Int , fielSha256: String )  {

     var fielSha256 : String= ""
     var contentLength : Int = 0
     var path : String = ""
     init {
         this.fielSha256 = fielSha256
         this.contentLength = contentLength
     }

     fun getSha256():String{
         return  this.fielSha256
     }

     fun getLength():Int{
         return  this.contentLength
     }

     fun getArtifactPath():String {
         return  this.path
     }

     fun getRepoId() :String{
         return ""
     }
}
