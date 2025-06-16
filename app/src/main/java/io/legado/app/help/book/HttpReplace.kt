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
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

import kotlinx.coroutines.runBlocking

object HttpReplace {

    /**
     * 根据 ReplaceRule 发起 HTTP 请求并返回结果。
     * 约束：始终只调用一次 response.body?.string()（此处 newCallStrResponse 已帮我们处理成 String）
     */
    fun request(rule: ReplaceRule, text: String): String? = runBlocking {
        val traceId = System.currentTimeMillis().toString()

        try {
            /* ---------- 1. 解析 Header / Params ---------- */
            val headers = GSON.fromJsonObject<Map<String, String>>(rule.httpHeaders).getOrNull()
                ?: emptyMap()

            val params = GSON.fromJsonObject<Map<String, Any?>>(rule.httpParams).getOrNull()
                ?.toMutableMap()
                ?: mutableMapOf()
            params["text"] = text

            val method = rule.httpMethod?.uppercase() ?: "POST"
            val url    = rule.httpUrl ?: return@runBlocking null

            /* ---------- 2. 统一日志 ---------- */
            AppLog.put("[$traceId] HttpReplace: ${rule.name} $method $url")
            if (headers.isNotEmpty()) AppLog.put("[$traceId] headers = $headers")
            if (params.isNotEmpty())  AppLog.put("[$traceId] params  = $params")

            /* ---------- 3. 发送请求 ---------- */
            val res = okHttpClient.newCallStrResponse {
                addHeaders(headers)
                when (method) {
                    "GET"  -> get(url, params.mapValues { it.value.toString() })
                    "POST" -> {
                        url(url)
                        val jsonBody = GSON.toJson(params)
                            .toRequestBody("application/json; charset=utf-8".toMediaType())
                        // 如用户未手动指定 Content-Type，则自动补一条
                        if (!headers.keys.any { it.equals("Content-Type", true) }) {
                            addHeader("Content-Type", "application/json; charset=utf-8")
                        }
                        post(jsonBody)
                    }
                    else   -> error("Unsupported method: $method")
                }
            }

            val body = res.body ?: return@runBlocking null
            AppLog.put("[$traceId] response = $body")

            /* ---------- 4. JSONPath (可选) ---------- */
            val final = rule.httpJsonPath
                ?.takeIf(String::isNotBlank)
                ?.let { path ->
                    runCatching { jsonPath.parse(body).read<String>(path) }
                        .getOrElse { body }      // 抽取失败 → 原文
                }
                ?: body

            AppLog.put("[$traceId] result   = $final")
            final
        }
        catch (t: Throwable) {
            // 日志里同样带上 traceId，方便前后关联
            AppLog.put("[$traceId] error: ${t.localizedMessage}", t)
            null
        }
    }
}
