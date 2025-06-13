package io.legado.app.help.book

import io.legado.app.data.entities.ReplaceRule
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.get
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postForm
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.jsonPath
import io.legado.app.constant.AppLog
import kotlinx.coroutines.runBlocking

object HttpReplace {
    fun request(rule: ReplaceRule, text: String): String? = runBlocking {
        try {
            val headers = GSON.fromJsonObject<Map<String, String>>(rule.httpHeaders).getOrNull() ?: emptyMap()
            val params = GSON.fromJsonObject<Map<String, String>>(rule.httpParams).getOrNull()?.toMutableMap() ?: mutableMapOf()
            params["text"] = text
            val method = rule.httpMethod?.uppercase() ?: "POST"
            AppLog.put("http replace request ${rule.httpUrl} ${method}")
            if (headers.isNotEmpty()) {
                AppLog.put("headers: ${'$'}headers")
            }
            if (params.isNotEmpty()) {
                AppLog.put("params: ${'$'}params")
            }
            val res = okHttpClient.newCallStrResponse {
                addHeaders(headers)
                if (method == "GET") {
                    get(rule.httpUrl ?: "", params)
                } else {
                    url(rule.httpUrl ?: "")
                    postForm(params.mapValues { it.value.toString() })
                }
            }
            val body = res.body ?: return@runBlocking null

            AppLog.put("http replace response ${'$'}body")

            rule.httpJsonPath?.takeIf { it.isNotBlank() }?.let { path ->
                val result = kotlin.runCatching {
                    jsonPath.parse(body).read<String>(path)
                }.getOrElse { body }
                AppLog.put("http replace result ${'$'}result")
                return@runBlocking result
            }
            return@runBlocking body
        } catch (e: Exception) {
            AppLog.put("http replace error ${'$'}{e.localizedMessage}", e)
            null
        }
    }
}
