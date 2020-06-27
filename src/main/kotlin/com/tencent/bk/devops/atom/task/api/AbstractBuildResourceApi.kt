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

import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.atom.api.SdkEnv
import com.tencent.bk.devops.atom.task.utils.JsonUtil
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.lang.RuntimeException
import java.nio.file.Paths
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

abstract class AbstractBuildResourceApi: BaseApi() {

    protected fun requestForResponse(
        request: Request,
        connectTimeoutInSec: Long? = null,
        readTimeoutInSec: Long? = null,
        writeTimeoutInSec: Long? = null,
        retryCount: Int = DEFAULT_RETRY_TIME
    ): Response {
        val builder = okHttpClient.newBuilder()
        if (connectTimeoutInSec != null) {
            builder.connectTimeout(connectTimeoutInSec, TimeUnit.SECONDS)
        }
        if (readTimeoutInSec != null) {
            builder.readTimeout(readTimeoutInSec, TimeUnit.SECONDS)
        }
        if (writeTimeoutInSec != null) {
            builder.writeTimeout(writeTimeoutInSec, TimeUnit.SECONDS)
        }
        val httpClient = builder.build()
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: Exception) {
            logger.error("Fail to request($request),error is :$e", e)
            throw RuntimeException("Fail to request($request),error is:${e.message}")
        }

        if (retryCodes.contains(response.code()) && retryCount > 0) {
            logger.warn(
                "Fail to request($request) with code ${response.code()} ," +
                    " message ${response.message()} and response (${response.body()?.string()}), retry after 3 seconds"
            )
            Thread.sleep(sleepTimeMills)
            return requestForResponse(request, connectTimeoutInSec, readTimeoutInSec, writeTimeoutInSec, retryCount - 1)
        }
        return response
    }

    protected fun request(
        request: Request,
        errorMessage: String,
        connectTimeoutInSec: Long? = null,
        readTimeoutInSec: Long? = null,
        writeTimeoutInSec: Long? = null
    ): String {

        requestForResponse(
            request = request,
            connectTimeoutInSec = connectTimeoutInSec,
            readTimeoutInSec = readTimeoutInSec,
            writeTimeoutInSec = writeTimeoutInSec
        ).use { response ->
            if (!response.isSuccessful) {
                val responseContent = response.body()?.string()
                logger.warn(
                    "Fail to request($request) with code ${response.code()} ," +
                        " message ${response.message()} and response ($responseContent)"
                )
                throw RuntimeException(errorMessage)
            }
            return response.body()!!.string()
        }
    }

    protected fun download(request: Request, destPath: File) {
        okHttpClient.newBuilder().build().newCall(request).execute().use { response ->
            download(response, destPath)
        }
    }

    private fun download(response: Response, destPath: File) {
        if (response.code() == 404) {
            throw RuntimeException("文件不存在")
        }
        if (!response.isSuccessful) {
            logger.info(response.body()!!.string())
            throw RuntimeException("获取文件失败")
        }
        if (!destPath.parentFile.exists()) destPath.parentFile.mkdirs()
        logger.info("save file >>>> ${destPath.canonicalPath}")

        response.body()!!.byteStream().use { bs ->
            val buf = ByteArray(BYTE_ARRAY_SIZE)
            var len = bs.read(buf)
            FileOutputStream(destPath).use { fos ->
                while (len != -1) {
                    fos.write(buf, 0, len)
                    len = bs.read(buf)
                }
            }
        }
    }

    companion object {
        val JsonMediaType = MediaType.parse("application/json; charset=utf-8")
        val OctetMediaType = MediaType.parse("application/octet-stream")
        val MultipartFormData = MediaType.parse("multipart/form-data")
        private const val EMPTY = ""
        private const val DEFAULT_RETRY_TIME = 5
        private const val sleepTimeMills = 5000L
        private const val BYTE_ARRAY_SIZE = 4096
        private const val CONNECT_TIMEOUT = 5L
        private const val READ_TIMEOUT = 1500L
        private const val WRITE_TIMEOUT = 60L
        private val retryCodes = arrayOf(502, 503)
        val logger = LoggerFactory.getLogger(AbstractBuildResourceApi::class.java)
    }

    private fun sslSocketFactory(): SSLSocketFactory {
        try {
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            return sslContext.socketFactory
        } catch (ingored: Exception) {
            throw RuntimeException(ingored.message!!)
        }
    }

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        @Throws(CertificateException::class)
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
        }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
        }

        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
            return arrayOf()
        }
    })

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS) // Set to 15 minutes
        .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
        .sslSocketFactory(sslSocketFactory(), trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()

    protected val objectMapper = JsonUtil.getObjectMapper()

    private fun buildUrl(path: String): String {
        return if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            SdkEnv.genUrl(path)
        }
    }

    fun encodeProperty(str: String): String {
        return str.replace(",", "%5C,")
            .replace("\\", "%5C\\")
            .replace("|", "%5C|")
            .replace("=", "%5C=")
    }

    fun purePath(destPath: String): String {
        return Paths.get(
            destPath.removeSuffix("/")
                .replace("./", "/")
                .replace("../", "/")
                .replace("//", "/")
        ).toString().replace("\\", "/") // 保证win/Unix平台兼容性统一转为/分隔文件路径
    }
}