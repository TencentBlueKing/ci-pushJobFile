package com.tencent.bk.devops.atom.task.pojo

import com.fasterxml.jackson.annotation.JsonProperty
import com.tencent.bk.devops.atom.task.api.FileTypeEnum

data class CreateFileTaskRequest(

    @JsonProperty("bk_app_code")
    val appCode: String,

    @JsonProperty("bk_app_secret")
    val appSecret: String,

    @JsonProperty("bk_username")
    val username: String,

    @JsonProperty("fileType")
    val fileType: FileTypeEnum,

    @JsonProperty("path")
    val path: String

)
