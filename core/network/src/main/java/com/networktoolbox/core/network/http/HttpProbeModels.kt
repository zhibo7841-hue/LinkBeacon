package com.networktoolbox.core.network.http

data class HttpProbeRequest(
    val executionUrl: String,
    val displayUrlRedacted: String,
    val plannedTransport: HttpTransportPath,
    val userAgent: String,
    val connectTimeoutMs: Int,
    val headersTimeoutMs: Int,
    val callTimeoutMs: Int,
) {
    init {
        require(executionUrl.isNotBlank())
        require(displayUrlRedacted.isNotBlank())
        require(userAgent.isNotBlank())
        require(connectTimeoutMs > 0)
        require(headersTimeoutMs > 0)
        require(callTimeoutMs > 0)
    }
}

enum class HttpTransportPath {
    DIRECT,
    HTTP_PROXY,
    PAC,
    UNKNOWN_PROXY,
}

enum class HttpProtocol {
    HTTP_1_0,
    HTTP_1_1,
    HTTP_2,
    UNKNOWN,
}

enum class HttpStatusCategory {
    SUCCESS_2XX,
    REDIRECT_3XX,
    CLIENT_RESPONSE_4XX,
    SERVER_RESPONSE_5XX,
    OTHER_STATUS,
}

enum class HttpFailureReason {
    CONNECT_TIMEOUT,
    HEADERS_TIMEOUT,
    CONNECTION_FAILED,
    TLS_FAILURE,
    PROXY_FAILURE,
    CLEARTEXT_NOT_PERMITTED,
    CANCELLED,
    NETWORK_CHANGED,
    INVALID_RESPONSE,
    UNEXPECTED_ERROR,
}

data class HttpResponseHeaders(
    val location: String?,
    val server: String?,
    val contentType: String?,
    val via: String?,
)

data class HttpProbeResult(
    val requestUrlRedacted: String,
    val method: String,
    val statusCode: Int?,
    val statusCategory: HttpStatusCategory?,
    val protocol: HttpProtocol?,
    val responseHeaders: HttpResponseHeaders,
    val durationMs: Long,
    val transportPath: HttpTransportPath,
    val bodyBytesRead: Long,
    val failureReason: HttpFailureReason?,
) {
    val responded: Boolean get() = statusCode != null && failureReason == null
}
