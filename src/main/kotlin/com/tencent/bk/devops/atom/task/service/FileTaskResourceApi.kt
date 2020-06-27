package com.tencent.bk.devops.atom.task.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.atom.task.pojo.CreateFileTaskRequest
import com.tencent.bk.devops.atom.task.pojo.FileTaskInfo
import com.tencent.bk.devops.atom.task.utils.OkhttpUtils
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory

class FileTaskResourceApi {

    companion object {
        private val objectMapper = ObjectMapper().registerModule(KotlinModule())
        private val logger = LoggerFactory.getLogger(FileTaskResourceApi::class.java)
    }

    fun createFileTask(
        esbHost: String,
        userId: String,
        projectId: String,
        pipelineId: String,
        buildId: String,
        createFileTaskRequest: CreateFileTaskRequest
    ): String {
        val url = "$esbHost/api/c/compapi/v3/apigw-app/artifactory/fileTask/projects/$projectId/pipelines/$pipelineId/builds/$buildId/create"
        val requestBody = objectMapper.writeValueAsString(createFileTaskRequest)
        val headerBuilder = Headers.Builder()
        headerBuilder.add("X-DEVOPS-UID", userId)
        val httpReq = Request.Builder()
            .url(url)
            .headers(headerBuilder.build())
            .post(RequestBody.create(MediaType.parse("application/json"), requestBody))
            .build()
        OkhttpUtils.doHttp(httpReq).use { resp ->
            val responseStr = resp.body()!!.string()
            logger.info("response headers: {}", resp.headers())
            logger.info("response body: $responseStr")
            if (resp.code() != 200) {
                logger.info("Response code error!|{}", resp.code())
                throw RuntimeException("createFileTask failed, resp code error!")
            }
            val response: com.tencent.bk.devops.atom.task.pojo.Result<String> = jacksonObjectMapper().readValue(responseStr)
            if (response.status == 0) {
                val taskId = response.data
                return taskId!!
            } else {
                val msg = response.message
                logger.error("createFileTask failed, msg: $msg")
                throw RuntimeException("createFileTask failed, msg: $msg")
            }
        }
    }

    fun getFileTaskInfo(
        esbHost: String,
        userId: String,
        projectId: String,
        pipelineId: String,
        buildId: String,
        taskId: String
    ): FileTaskInfo? {
        val url = "$esbHost/api/c/compapi/v3/apigw-app/artifactory/fileTask/projects/$projectId/pipelines/$pipelineId/builds/$buildId/tasks/$taskId/status"
        val headerBuilder = Headers.Builder()
        headerBuilder.add("X-DEVOPS-UID", userId)
        val httpReq = Request.Builder()
            .url(url)
            .headers(headerBuilder.build())
            .get()
            .build()
        OkhttpUtils.doHttp(httpReq).use { resp ->
            val responseStr = resp.body()!!.string()
            logger.info("response headers: {}", resp.headers())
            logger.info("response body: $responseStr")
            if (resp.code() != 200) {
                logger.info("Response code error!|{}", resp.code())
                throw RuntimeException("getFileTaskInfo failed, resp code error!")
            }
            val response: com.tencent.bk.devops.atom.task.pojo.Result<FileTaskInfo> = jacksonObjectMapper().readValue(responseStr)
            if (response.status == 0) {
                return response.data
            } else {
                val msg = response.message
                logger.error("getFileTaskInfo failed, msg: $msg")
                throw RuntimeException("getFileTaskInfo failed, msg: $msg")
            }
        }
    }

    fun clearFileTask(
        esbHost: String,
        userId: String,
        projectId: String,
        pipelineId: String,
        buildId: String,
        taskId: String
    ): Boolean {
        val url = "$esbHost/api/c/compapi/v3/apigw-app/artifactory/fileTask/projects/$projectId/pipelines/$pipelineId/builds/$buildId/tasks/$taskId/clear"
        val headerBuilder = Headers.Builder()
        headerBuilder.add("X-DEVOPS-UID", userId)
        val httpReq = Request.Builder()
            .url(url)
            .headers(headerBuilder.build())
            .put(RequestBody.create(MediaType.parse("application/json"), "{}"))
            .build()
        OkhttpUtils.doHttp(httpReq).use { resp ->
            val responseStr = resp.body()!!.string()
            logger.info("response headers: {}", resp.headers())
            logger.info("response body: $responseStr")
            if (resp.code() != 200) {
                logger.info("Response code error!|{}", resp.code())
                throw RuntimeException("clearFileTask failed, resp code error!")
            }
            val response: com.tencent.bk.devops.atom.task.pojo.Result<Boolean> = jacksonObjectMapper().readValue(responseStr)
            if (response.status == 0) {
                return response.data!!
            } else {
                val msg = response.message
                logger.error("clearFileTask failed, msg: $msg")
                throw RuntimeException("clearFileTask failed, msg: $msg")
            }
        }
    }
}
