package com.tencent.bk.devops.atom.task.utils

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.jvm.internal.Intrinsics

object JsonUtils {

    fun toList(json: String): List<String> {
        val objectMapper = ObjectMapper()
        Intrinsics.checkParameterIsNotNull(json, "json")
        return objectMapper.readValue(json, object : TypeReference<Any?>() {} as TypeReference<*>)
    }
}