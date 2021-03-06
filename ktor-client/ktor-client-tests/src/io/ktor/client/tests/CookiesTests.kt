package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.backend.*
import io.ktor.client.features.cookies.*
import io.ktor.client.pipeline.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.host.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.experimental.*
import org.junit.*


open class CookiesTests(factory: HttpClientBackendFactory) : TestWithKtor(factory) {
    override val server: ApplicationHost = embeddedServer(Jetty, 8080) {
        routing {
            get("/") {
                val cookie = Cookie("hello-cookie", "my-awesome-value")
                context.response.cookies.append(cookie)

                context.respond("Done")
            }
            get("/update-user-id") {
                val id = context.request.cookies["id"]?.toInt() ?: let {
                    context.response.status(HttpStatusCode.Forbidden)
                    context.respondText("Forbidden")
                    return@get
                }

                val cookie = Cookie("id", (id + 1).toString())
                context.response.cookies.append(cookie)

                context.respond("Done")
            }
        }
    }

    @Test
    fun testAccept() {
        val client = createClient().config {
            install(HttpCookies)
        }

        runBlocking { client.get<Unit>(port = 8080) }

        client.cookies("localhost").let {
            assert(it.size == 1)
            assert(it["hello-cookie"]!!.value == "my-awesome-value")
        }

        client.close()
    }

    @Test
    fun testUpdate() {
        val client = createClient().config {
            install(HttpCookies) {
                default {
                    set("localhost", Cookie("id", "1"))
                }
            }
        }

        for (i in 1..10) {
            val before = client.getId()
            runBlocking { client.get<Unit>(path = "update-user-id", port = 8080) }
            assert(client.getId() == before + 1)
        }

        client.close()
    }

    @Test
    fun testConstant() {
        val client = createClient().config {
            install(HttpCookies) {
                storage = ConstantCookieStorage(Cookie("id", "1"))
            }
        }

        runBlocking {
            client.get<Unit>(path = "update-user-id", port = 8080)
        }
        assert(client.getId() == 1)
        runBlocking { client.get<Unit>(path = "update-user-id", port = 8080) }
        assert(client.getId() == 1)
    }

    @Test
    fun multipleClients() {
        /* a -> b
         * |    |
         * c    d
         */
        val client = createClient()
        val a = client.config { install(HttpCookies) { default { set("localhost", Cookie("id", "1")) } } }
        val b = a.config { install(HttpCookies) { default { set("localhost", Cookie("id", "10")) } } }
        val c = a.config { }
        val d = b.config { }

        runBlocking {
            a.get<Unit>(path = "update-user-id", port = 8080)
        }

        assert(a.getId() == 2)
        assert(c.getId() == 2)
        assert(b.getId() == 10)
        assert(d.getId() == 10)

        runBlocking {
            b.get<Unit>(path = "update-user-id", port = 8080)
        }

        assert(a.getId() == 2)
        assert(c.getId() == 2)
        assert(b.getId() == 11)
        assert(d.getId() == 11)

        runBlocking {
            c.get<Unit>(path = "update-user-id", port = 8080)
        }

        assert(a.getId() == 3)
        assert(c.getId() == 3)
        assert(b.getId() == 11)
        assert(d.getId() == 11)

        runBlocking {
            d.get<Unit>(path = "update-user-id", port = 8080)
        }

        assert(a.getId() == 3)
        assert(c.getId() == 3)
        assert(b.getId() == 12)
        assert(d.getId() == 12)

        client.close()
    }

    private fun HttpClient.getId() = cookies("localhost")["id"]?.value?.toInt()!!
}
