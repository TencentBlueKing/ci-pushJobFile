package com.tencent.bk.devops.atom.task.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory

object HttpUtils {

	val logger = LoggerFactory.getLogger(this::class.java)

	fun get(url: String, headerMap: Map<String, String>): String{
		val httpReq = Request.Builder()
				.url(url).headers(Headers.of(headerMap))
				.get()
				.build()
		OkhttpUtils.doHttp(httpReq).use { resp ->
			val responseContent = resp.body()!!.string()
			val response: Map<String, Any> = jacksonObjectMapper().readValue(responseContent)
			if (response["status"] == 0) {
				return response["data"].toString()
			} else {
				val msg = response["message"] as String
				logger.error("start job failed, msg: $msg")
				throw RuntimeException("start job failed, msg: $msg")
			}
		}
	}

	fun post(url: String, headerMap: Map<String, String>?,requestBody: String): String{
		val httpReq = Request.Builder()
				.url(url).headers(Headers.of(headerMap))
				.post(RequestBody.create(MediaType.parse("application/json"), requestBody))
				.build()
		OkhttpUtils.doHttp(httpReq).use { resp ->
			val responseStr = resp.body()!!.string()
			logger.info("response body: $responseStr")

			val response: Map<String, Any> = jacksonObjectMapper().readValue(responseStr)
			if (response["status"] == 0) {
				return response["data"].toString()
			} else {
				val msg = response["message"] as String
				logger.error("start job failed, msg: $msg")
				throw RuntimeException("start job failed, msg: $msg")
			}
		}
	}
}