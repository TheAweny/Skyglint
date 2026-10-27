package dev.skyglint.updates.github

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

/** No credentials, player identity, cookies, automatic redirects or unbounded response buffering. */
class GitHubHttpTransport : UpdateTransport {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun fetch(uri: URI, maximumBytes: Int): UpdateHttpResponse {
        require(GitHubUpdateService.allowedNetworkUri(uri)) { "untrusted_update_host" }
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
            .header("Accept", if (uri.host == "api.github.com") "application/vnd.github+json" else "application/octet-stream")
            .header("User-Agent", "Skyglint-Updater")
            .apply { if (uri.host == "api.github.com") header("X-GitHub-Api-Version", "2026-03-10") }
            .GET().build()
        // JDK 25 limiting cancels upstream before accepting oversized content. The future deadline
        // also bounds a server that sends headers and then stalls the response body.
        val pending = client.sendAsync(request, HttpResponse.BodyHandlers.limiting(
            HttpResponse.BodyHandlers.ofByteArray(), maximumBytes.toLong()))
        try {
            val response = pending.get(35, TimeUnit.SECONDS)
            return UpdateHttpResponse(response.statusCode(), response.body(), response.headers().firstValue("location").orElse(null))
        } finally {
            if (!pending.isDone) pending.cancel(true)
        }
    }
}
