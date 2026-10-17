package io.github.vil4max.drivecheck.provider

import kotlin.time.Clock
import kotlin.time.Instant

// A live response recorded with one request on 2026-10-17 (26 keys: the 25 app regions and
// Sevastopol, which is not an app region).
const val LIVE_FEED = """{"source":"Vadym Klymenko API (default)","cachedat":"2026-10-17 13:06:54","states":{
"Севастополь":{"alertnow":true,"changed":"2022-12-11 00:22:00"},
"Вінницька область":{"alertnow":true,"changed":"1970-01-01 03:00:00"},
"Волинська область":{"alertnow":false,"changed":"1970-01-01 03:00:00"},
"Дніпропетровська область":{"alertnow":true,"changed":"1970-01-01 03:00:00"},
"Донецька область":{"alertnow":true,"changed":"1970-01-01 03:00:00"},
"Житомирська область":{"alertnow":true,"changed":"1970-01-01 03:00:00"},
"Закарпатська область":{"alertnow":false,"changed":"1970-01-01 03:00:00"},
"Запорізька область":{"alertnow":true,"changed":"1970-01-01 03:00:00"},
"Івано-Франківська область":{"alertnow":false,"changed":"1970-01-01 03:00:00"},
"Київська область":{"alertnow":true,"changed":"1970-01-01 03:00:00"},
"Кіровоградська область":{"alertnow":true,"changed":"1970-01-01 03:00:00"},
"Луганська область":{"alertnow":true,"changed":"2022-04-04 19:45:39"},
"Львівська область":{"alertnow":false,"changed":"1970-01-01 03:00:00"},
"Миколаївська область":{"alertnow":true,"changed":"1970-01-01 03:00:00"},
"Одеська область":{"alertnow":true,"changed":"1970-01-01 03:00:00"},
"Полтавська область":{"alertnow":false,"changed":"1970-01-01 03:00:00"},
"Рівненська область":{"alertnow":true,"changed":"1970-01-01 03:00:00"},
"Сумська область":{"alertnow":true,"changed":"1970-01-01 03:00:00"},
"Тернопільська область":{"alertnow":false,"changed":"1970-01-01 03:00:00"},
"Харківська область":{"alertnow":true,"changed":"1970-01-01 03:00:00"},
"Херсонська область":{"alertnow":true,"changed":"1970-01-01 03:00:00"},
"Хмельницька область":{"alertnow":false,"changed":"1970-01-01 03:00:00"},
"Черкаська область":{"alertnow":true,"changed":"1970-01-01 03:00:00"},
"Чернівецька область":{"alertnow":false,"changed":"1970-01-01 03:00:00"},
"Чернігівська область":{"alertnow":true,"changed":"1970-01-01 03:00:00"},
"м. Київ":{"alertnow":false,"changed":"1970-01-01 03:00:00"}}}"""

const val LIVE_FEED_ALARM_KEYS = "Вінницька область,Дніпропетровська область,Донецька область," +
    "Житомирська область,Запорізька область,Київська область,Кіровоградська область," +
    "Луганська область,Миколаївська область,Одеська область,Рівненська область,Сумська область," +
    "Харківська область,Херсонська область,Черкаська область,Чернігівська область"

fun feed(cachedAt: String = "2026-10-17 13:06:54", kyivAlarm: Boolean = false, withKyiv: Boolean = true): String {
    val kyiv = if (withKyiv) ""","м. Київ":{"alertnow":$kyivAlarm,"changed":"2026-10-17 12:00:00"}""" else ""
    return """{"source":"test","cachedat":"$cachedAt","states":{"Львівська область":{"alertnow":false,"changed":"2026-10-17 12:00:00"}$kyiv}}"""
}

fun jsonResponse(body: String, status: Int = 200, headers: Map<String, String> = emptyMap()) =
    HttpResponse(status, mapOf("content-type" to "application/json") + headers, body.encodeToByteArray())

class FakeTransport(responses: List<suspend (HttpRequest) -> HttpResponse>) : HttpTransport {
    private val queue = ArrayDeque(responses)
    val requests = mutableListOf<HttpRequest>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        val next = queue.removeFirstOrNull() ?: error("FakeTransport: no response left for request ${requests.size}")
        return next(request)
    }

    companion object {
        fun answering(vararg responses: HttpResponse) = FakeTransport(responses.map { response -> { _ -> response } })
    }
}

class TestClock(var now: Instant = Instant.parse("2026-10-17T10:00:00Z")) : Clock {
    override fun now(): Instant = now
}

class RecordingLog : ProviderLog {
    val lines = mutableListOf<String>()
    override fun info(message: String) {
        lines += message
    }
}
