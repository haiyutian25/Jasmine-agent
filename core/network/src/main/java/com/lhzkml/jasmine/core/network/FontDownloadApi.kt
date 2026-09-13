package com.lhzkml.jasmine.core.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Streaming download contract for large binary assets (preset fonts).
 *
 * Kept separate from the JSON-based service interfaces because downloads bypass
 * conversion entirely:
 * [Streaming] stops Retrofit from buffering the payload into memory, so the
 * caller can hash and write the byte stream incrementally. Non-2xx responses
 * surface as [retrofit2.HttpException].
 */
interface FontDownloadApi {

    /** Streams the resource at [url]; absolute URLs replace the base URL. */
    @Streaming
    @GET
    suspend fun download(@Url url: String): ResponseBody
}
