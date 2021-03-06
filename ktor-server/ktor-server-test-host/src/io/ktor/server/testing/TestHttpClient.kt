package io.ktor.server.testing

import io.ktor.cio.*
import io.ktor.client.jvm.*
import io.ktor.http.*
import io.ktor.util.*
import java.io.*
import java.util.concurrent.*

class TestingHttpClient(private val applicationHost: TestApplicationHost) : HttpClient(), AutoCloseable {
    override suspend fun openConnection(host: String, port: Int, secure: Boolean): HttpConnection {
        return TestingHttpConnection(applicationHost)
    }

    override fun close() {
        applicationHost.stop(0L, 0L, TimeUnit.MILLISECONDS)
    }

    private class TestingHttpConnection(val app: TestApplicationHost) : HttpConnection {

        suspend override fun request(configure: RequestBuilder.() -> Unit): HttpResponse {
            val builder = RequestBuilder()
            builder.configure()

            val call = app.handleRequest(builder.method, builder.path) {
                builder.headers().forEach {
                    addHeader(it.first, it.second)
                }

                builder.body?.let { content ->
                    val bos = ByteArrayOutputStream()
                    content(bos)
                    body = bos.toByteArray().toString(Charsets.UTF_8)
                }
            }

            return TestingHttpResponse(this, call)
        }

        private class TestingHttpResponse(override val connection: HttpConnection, val call: TestApplicationCall) : HttpResponse {

            override val version: String
                get() = "HTTP/1.1"

            override val channel: ReadChannel
                get() = call.response.byteContent?.toReadChannel() ?: EmptyReadChannel

            override val headers: ValuesMap
                get() = call.response.headers.allValues()

            override val status: HttpStatusCode
                get() = call.response.status() ?: throw IllegalArgumentException("There is no status code assigned")
        }

        override fun close() {}
    }
}

