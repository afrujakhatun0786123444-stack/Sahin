package com.example.network

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class L99CommandResponse(
    val action: String = "NONE",
    val argument: String = "",
    val assistant_reply: String = ""
) {
    companion object {
        private val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        private val adapter = moshi.adapter(L99CommandResponse::class.java)

        fun parse(rawText: String): L99CommandResponse {
            val cleaned = cleanJson(rawText)
            return try {
                adapter.fromJson(cleaned) ?: L99CommandResponse(assistant_reply = rawText)
            } catch (e: Exception) {
                // Regex fallbacks if JSON fails to parse
                val actionRegex = """(?i)"action"\s*:\s*"([^"]+)"""".toRegex()
                val argumentRegex = """(?i)"argument"\s*:\s*"([^"]+)"""".toRegex()
                val replyRegex = """(?i)"assistant_reply"\s*:\s*"([^"]+)"""".toRegex()

                val action = actionRegex.find(cleaned)?.groupValues?.get(1) ?: "NONE"
                val argument = argumentRegex.find(cleaned)?.groupValues?.get(1) ?: ""
                val reply = replyRegex.find(cleaned)?.groupValues?.get(1) ?: rawText

                L99CommandResponse(
                    action = action,
                    argument = argument,
                    assistant_reply = reply.replace("\\n", "\n").replace("\\\"", "\"")
                )
            }
        }

        private fun cleanJson(text: String): String {
            var result = text.trim()
            if (result.startsWith("```")) {
                result = result.substringAfter("```")
                if (result.startsWith("json")) {
                    result = result.substringAfter("json")
                }
                result = result.substringBeforeLast("```")
            }
            return result.trim()
        }
    }
}
