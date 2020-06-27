package com.tencent.bk.devops.atom.task.pojo

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author  citruswang
 * @since 2/12/2019 10:54
 */
data class FastPushFileRequestV2(

    @JsonProperty("bk_app_code")
    val appCode: String,

    @JsonProperty("bk_app_secret")
    val appSecret: String,

    @JsonProperty("bk_username")
    val username: String,

    @JsonProperty("bk_biz_id")
    val bizId: Long,

    @JsonProperty("account")
    val account: String,

    @JsonProperty("file_target_path")
    val fileTargetPath: String,

    @JsonProperty("file_source")
    val fileSource: List<FastPushFileSource>,

    @JsonProperty("custom_query_id")
    val customQueryId: List<String>,

    @JsonProperty("ip_list")
    val ipList: List<FastPushIpDTO>,

    @JsonProperty("bk_callback_url")
    val callbackUrl: String


)
