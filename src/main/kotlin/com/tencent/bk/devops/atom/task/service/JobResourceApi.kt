package com.tencent.bk.devops.atom.task.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.atom.task.pojo.FastPushFileRequestV2
import com.tencent.bk.devops.atom.task.utils.OkhttpUtils
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory

class JobResourceApi {

    companion object {
        private val objectMapper = ObjectMapper().registerModule(KotlinModule())
        private val logger = LoggerFactory.getLogger(JobResourceApi::class.java)
    }

    fun fastPushFileV2(pushFileRequest: FastPushFileRequestV2, esbHost: String): Long {
        val url = "$esbHost/api/c/compapi/v2/job/fast_push_file/"
        val requestBody = objectMapper.writeValueAsString(pushFileRequest)
        val taskInstanceId = sendTaskRequest(requestBody, url)
        if (taskInstanceId <= 0) {
            // 失败处理
            logger.error("start jobDevOpsFastPushfile failed")
            throw RuntimeException("start jobDevOpsFastPushfile failed")
        }
        return taskInstanceId
    }

    fun getTaskResult(appId: String, appSecret: String, bizId: String, taskInstanceId: Long, operator: String, esbHost: String): TaskResult {
        try {
            val url = "$esbHost/api/c/compapi/v2/job/get_job_instance_status/?bk_app_code=$appId&bk_app_secret=$appSecret&bk_username=$operator&bk_biz_id=$bizId&job_instance_id=$taskInstanceId"
            OkhttpUtils.doGet(url).use { resp ->
                val responseStr = resp.body()!!.string()
                val response: Map<String, Any> = jacksonObjectMapper().readValue(responseStr)
                if (response["code"] == 0) {
                    val responseData = response["data"] as Map<String, Any>
                    val instanceData = responseData["job_instance"] as Map<String, Any>
                    val status = instanceData["status"] as Int
                    return when (status) {
                        3 -> {
                            logger.info("Job execute task finished and success")
                            TaskResult(true, true, "Success")
                        }
                        4 -> {
                            logger.error("Job execute task failed")
                            TaskResult(true, false, "Job failed")
                        }
                        else -> {
                            logger.info("Job execute task running")
                            TaskResult(false, false, "Job Running")
                        }
                    }
                } else {
                    val msg = response["message"] as String
                    logger.error("job execute failed, msg: $msg")
                    throw RuntimeException("job execute failed, msg: $msg")
                }
            }
        } catch (e: Exception) {
            logger.error("execute job error", e)
            throw RuntimeException("execute job error: ${e.message}")
        }
    }

    private fun sendTaskRequest(requestBody: String, url: String): Long {
        val httpReq = Request.Builder()
            .url(url)
            .post(RequestBody.create(MediaType.parse("application/json"), requestBody))
            .build()
        OkhttpUtils.doHttp(httpReq).use { resp ->
            val responseStr = resp.body()!!.string()
            logger.info("response headers: {}", resp.headers())
            logger.info("response body: $responseStr")
            if (resp.code() != 200) {
                logger.info("Response code error!|{}", resp.code())
                throw RuntimeException("start job failed, resp code error!")
            }
            val response: Map<String, Any> = jacksonObjectMapper().readValue(responseStr)
            if (response["code"] == 0) {
                val responseData = response["data"] as Map<String, Any>
                val taskInstanceId = responseData["job_instance_id"].toString().toLong()
                logger.info("start job success, taskInstanceId: $taskInstanceId")
                return taskInstanceId
            } else {
                val msg = response["message"] as String
                logger.error("start job failed, msg: $msg")
                throw RuntimeException("start job failed, msg: $msg")
            }
        }
    }

    data class TaskResult(val isFinish: Boolean, val success: Boolean, val msg: String)
}
