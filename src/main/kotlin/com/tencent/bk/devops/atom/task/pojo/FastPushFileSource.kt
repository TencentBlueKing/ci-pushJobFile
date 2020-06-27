package com.tencent.bk.devops.atom.task.pojo

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author  citruswang
 * @since 2/12/2019 11:18
 */
data class FastPushFileSource(

    @JsonProperty("files")
    val file: List<String>,

    @JsonProperty("account")
    val account: String,

    @JsonProperty("custom_query_id")
    val customQueryId: List<String>,

    @JsonProperty("ip_list")
    val ipList: List<FastPushIpDTO>
)