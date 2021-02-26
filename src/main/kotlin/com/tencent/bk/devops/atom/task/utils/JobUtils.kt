package com.tencent.bk.devops.atom.task.utils

import com.tencent.bk.devops.atom.task.pojo.FastPushFileRequestV2
import com.tencent.bk.devops.atom.task.service.JobResourceApi

object JobUtils {

    private val jobResourceApi = JobResourceApi()

    fun fastPushFileV2(pushFileRequest: FastPushFileRequestV2, jobHost: String): Long {
        return jobResourceApi.fastPushFileV2(pushFileRequest, jobHost)
    }

    fun getTaskResult(
        appId: String,
        appSecret: String,
        bizId: String,
        taskInstanceId: Long,
        operator: String,
        jobHost: String
    ): JobResourceApi.TaskResult {
        return jobResourceApi.getTaskResult(appId, appSecret, bizId, taskInstanceId, operator, jobHost)
    }

    fun getDetailUrl(jobHost: String, appId: String, taskInstanceId: Long): String {
        return "<a target='_blank' href='$jobHost/?taskInstanceList&appId=$appId#taskInstanceId=$taskInstanceId'>到作业平台V2查看详情(Go to JobV2 for Detail, click this if using BlueKing5.x)</a>  <a target='_blank' href='$jobHost/api_execute/$taskInstanceId'>到作业平台V3查看详情(Go to JobV3 for Detail, click this if using BlueKing6.x)</a>"
    }
}
