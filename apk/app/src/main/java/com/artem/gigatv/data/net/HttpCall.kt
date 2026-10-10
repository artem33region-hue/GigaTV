package com.artem.gigatv.data.net

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** Keep cancellation attached until the response body has been consumed and closed. */
internal suspend fun <T> Call.awaitParsed(parse: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use(parse)
                    continuation.resume(result)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }
