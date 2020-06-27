/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bk.devops.atom.task.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.atom.task.pojo.CreateFileTaskRequest
import com.tencent.bk.devops.atom.task.pojo.FileTaskInfo
import com.tencent.bk.devops.atom.task.pojo.artifactory.GetFileDownloadUrlsResponse
import com.tencent.bk.devops.atom.task.pojo.Result
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import java.io.File

class ArchiveResourceApi : AbstractBuildResourceApi(), ArchiveSDKApi {
    companion object {
        private val objectMapper = ObjectMapper().registerModule(KotlinModule())
        private val logger = LoggerFactory.getLogger(ArchiveResourceApi::class.java)
    }

    override fun createFileTask(
        userId: String,
        projectId: String,
        pipelineId: String,
        buildId: String,
        createFileTaskRequest: CreateFileTaskRequest
    ): String {
        val url = "/ms/artifactory/api/build/artifactories/filetask/create"
        logger.info("url=$url")
        val requestBody = objectMapper.writeValueAsString(createFileTaskRequest)
        val headerMap = mutableMapOf<String, String>()
        headerMap[DevOpsHeaders.AUTH_HEADER_DEVOPS_USER_ID] = userId
        headerMap[DevOpsHeaders.AUTH_HEADER_DEVOPS_PROJECT_ID] = projectId
        headerMap[DevOpsHeaders.AUTH_HEADER_DEVOPS_PIPELINE_ID] = pipelineId
        headerMap[DevOpsHeaders.AUTH_HEADER_DEVOPS_BUILD_ID] = buildId
        val request = buildPost(url, RequestBody.create(MediaType.parse("application/json"), requestBody), headerMap)
        val response = request(request, "Error occured when createFileTask")
        try {
            val result = objectMapper.readValue<Result<String>>(response)
            if (result.isNotOk()) {
                throw RuntimeException(result.message ?: "Error occured when createFileTask:${result.status.toLong()}")
            }
            return result.data!!
        } catch (ignored: Exception) {
            logger.info(ignored.message ?: "")
            throw RuntimeException("createFileTask fail: $response")
        }
    }

    override fun getFileTaskInfo(
        userId: String,
        projectId: String,
        pipelineId: String,
        buildId: String,
        taskId: String
    ): FileTaskInfo? {
        val url = "/ms/artifactory/api/build/artifactories/filetask/tasks/$taskId/status"
        logger.info("url=$url")
        val headerMap = mutableMapOf<String, String>()
        headerMap[DevOpsHeaders.AUTH_HEADER_DEVOPS_USER_ID] = userId
        headerMap[DevOpsHeaders.AUTH_HEADER_DEVOPS_PROJECT_ID] = projectId
        headerMap[DevOpsHeaders.AUTH_HEADER_DEVOPS_PIPELINE_ID] = pipelineId
        headerMap[DevOpsHeaders.AUTH_HEADER_DEVOPS_BUILD_ID] = buildId
        val request = buildGet(url, headerMap)
        val response = request(request, "Error occured when getFileTaskInfo")
        try {
            val result = objectMapper.readValue<Result<FileTaskInfo>>(response)
            if (result.isNotOk()) {
                throw RuntimeException(result.message ?: "Error occured when getFileTaskInfo:${result.status.toLong()}")
            }
            return result.data!!
        } catch (ignored: Exception) {
            logger.info(ignored.message ?: "")
            throw RuntimeException("getFileTaskInfo fail: $response")
        }
    }

    override fun clearFileTask(
        userId: String,
        projectId: String,
        pipelineId: String,
        buildId: String,
        taskId: String
    ): Boolean {
        val url = "/ms/artifactory/api/build/artifactories/filetask/tasks/$taskId/clear"
        logger.info("url=$url")
        val headerMap = mutableMapOf<String, String>()
        headerMap[DevOpsHeaders.AUTH_HEADER_DEVOPS_USER_ID] = userId
        headerMap[DevOpsHeaders.AUTH_HEADER_DEVOPS_PROJECT_ID] = projectId
        headerMap[DevOpsHeaders.AUTH_HEADER_DEVOPS_PIPELINE_ID] = pipelineId
        headerMap[DevOpsHeaders.AUTH_HEADER_DEVOPS_BUILD_ID] = buildId
        val request = buildPut(url, headerMap)
        val response = request(request, "Error occured when clearFileTask")
        try {
            val result = objectMapper.readValue<Result<Boolean>>(response)
            if (result.isNotOk()) {
                throw RuntimeException(result.message ?: "Error occured when clearFileTask:${result.status.toLong()}")
            }
            return result.data!!
        } catch (ignored: Exception) {
            logger.info(ignored.message ?: "")
            throw RuntimeException("clearFileTask fail: $response")
        }
    }

    override fun getFileDownloadUrls(
        userId: String,
        projectId: String,
        pipelineId: String,
        buildId: String,
        fileType: FileTypeEnum,
        customFilePath: String?
    ): List<String> {
        val purePath = if (customFilePath != null) {
            purePath(customFilePath)
        } else {
            customFilePath
        }
        val url =
            "/ms/artifactory/api/build/artifactories/pipeline/$pipelineId/build/$buildId/file/download/urls/get?fileType=$fileType&customFilePath=$purePath"
        logger.info("url=$url")
        val request = buildGet(url)
        val response = request(request, "Error occured when getFileDownloadUrls")
        val result = try {
            objectMapper.readValue<Result<GetFileDownloadUrlsResponse?>>(response)
        } catch (ignored: Exception) {
            logger.info(ignored.message ?: "")
            throw RuntimeException("archive fail: $response")
        }
        if (result.isNotOk()) {
            throw RuntimeException(result.message ?: "Error occured when getFileDownloadUrls:${result.status.toLong()}")
        }

        return result.data?.fileUrlList ?: emptyList()
    }

    override fun downloadCustomizeFile(userId: String, projectId: String, uri: String, destPath: File) {
        val url = if (uri.startsWith("http://") || uri.startsWith("https://")) {
            uri
        } else {
            "/ms/artifactory/api/build/artifactories/file/archive/download?fileType=${FileTypeEnum.BK_CUSTOM}&customFilePath=$uri"
        }
        val request = buildGet(url)
        download(request, destPath)
    }

    override fun downloadPipelineFile(userId: String, projectId: String, pipelineId: String, buildId: String, uri: String, destPath: File) {
        val url = if (uri.startsWith("http://") || uri.startsWith("https://")) {
            uri
        } else {
            "/ms/artifactory/api/build/artifactories/file/archive/download?fileType=${FileTypeEnum.BK_ARCHIVE}&customFilePath=$uri"
        }
        val request = buildGet(url)
        download(request, destPath)
    }

    override fun dockerBuildCredential(projectId: String): Map<String, String> {
        return hashMapOf()
    }

    override fun uploadFile(
        url: String,
        destPath: String,
        file: File,
        headers: Map<String, String>?
    ): Result<Boolean> {
        logger.info("upload file url >>> $url")
        val fileBody = RequestBody.create(MultipartFormData, file)
        val fileName = file.name
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, fileBody)
            .build()
        val request = buildPost(url, requestBody, headers ?: emptyMap())
        val responseContent = request(request, "upload file:$fileName fail")
        return objectMapper.readValue(responseContent)
    }
}