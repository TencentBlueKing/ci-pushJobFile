package com.tencent.bk.devops.atom.task.pojo

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author  citruswang
 * @since 2/12/2019 11:19
 */
data class FastPushIpDTO(
    @JsonProperty("ip")
    val ip: String,

    @JsonProperty("bk_cloud_id")
    val cloudId: Long
)