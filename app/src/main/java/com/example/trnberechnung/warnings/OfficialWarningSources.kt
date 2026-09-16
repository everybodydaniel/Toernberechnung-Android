package com.example.trnberechnung.warnings

import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class BshWarningSource : WarningSourceAdapter {
    override val source: WarningSourceId = WarningSourceId.BSH
    override val documents: List<OfficialWarningDocument> = listOf(BSH_NORTH_SEA_DOCUMENT)

    override suspend fun fetchActiveWarnings(now: Instant): WarningSourceResult =
        WarningSourceResult.Success(
            WarningSourceSnapshot(
                source = source,
                warnings = emptyList(),
                fetchedAt = now,
                isComplete = true,
            ),
        )

    companion object {
        val BSH_NORTH_SEA_DOCUMENT =
            OfficialWarningDocument(
                id = "bsh-nwn-north-sea",
                title = "BSH Nautische Warnnachrichten Nordsee",
                issuer = "BSH · Seewarndienst Emden",
                description = "Amtliche, fortlaufend aktualisierte Warnnachrichten für den deutschen Nordseebereich.",
                url = "https://www2.bsh.de/aktdat/nwn/nwn-nord.pdf",
                linkKind = OfficialLinkKind.PDF,
            )
    }
}

class ElwisWarningSource(
    private val gateway: ElwisHttpGateway = OkHttpElwisGateway(),
) : WarningSourceAdapter {
    override val source: WarningSourceId = WarningSourceId.ELWIS
    override val documents: List<OfficialWarningDocument> = listOf(ELWIS_NORTH_SEA_DOCUMENT)

    override suspend fun fetchActiveWarnings(now: Instant): WarningSourceResult =
        runCatching {
            val date = now.atZone(BERLIN_ZONE).toLocalDate()
            val searchHtml = gateway.get(ELWIS_SEARCH_URL)
            val csrfToken =
                ElwisHtmlParser.parseCsrfToken(searchHtml)
                    ?: throw ElwisParseException("Das ELWIS-Suchformular ist derzeit nicht lesbar.")
            val dateParts = DATE_PARTS_FORMATTER.format(date).split('|')
            val firstPageHtml =
                gateway.postForm(
                    url = ELWIS_LIST_URL,
                    fields =
                        linkedMapOf(
                            "form[gueltigVonTag]" to dateParts[0],
                            "form[gueltigVonMonat]" to dateParts[1],
                            "form[gueltigVonJahr]" to dateParts[2],
                            "form[gueltigBisTag]" to dateParts[0],
                            "form[gueltigBisMonat]" to dateParts[1],
                            "form[gueltigBisJahr]" to dateParts[2],
                            "form[herausgeber]" to "0",
                            "form[submit]" to "Suche starten...",
                            "form[_token]" to csrfToken,
                        ),
                )
            val firstPage = ElwisHtmlParser.parseListPage(firstPageHtml)
            val summaries = firstPage.notices.toMutableList()
            var skippedRows = firstPage.skippedNoticeRows
            var allPagesLoaded = true
            val loadedPages = mutableSetOf(1)
            val loadedNoticeIds = firstPage.notices.mapTo(mutableSetOf(), ElwisNoticeSummary::officialId)
            val pendingPages = ArrayDeque(firstPage.pageNumbers.filter { it > 1 }.sorted())
            var additionalPagesLoaded = 0
            while (pendingPages.isNotEmpty() && additionalPagesLoaded < MAX_ADDITIONAL_PAGES) {
                val page = pendingPages.removeFirst()
                if (!loadedPages.add(page)) continue
                val html = gateway.get("$ELWIS_BASE_URL/DE/dynamisch/Bfs/changePage:$page:elwis_bfs_showBfs")
                val parsed = ElwisHtmlParser.parseListPage(html)
                val newNotices = parsed.notices.filter { notice -> notice.officialId !in loadedNoticeIds }
                if (
                    parsed.notices.isEmpty() ||
                    newNotices.isEmpty() ||
                    newNotices.size != parsed.notices.size
                ) {
                    allPagesLoaded = false
                }
                summaries += newNotices
                loadedNoticeIds += newNotices.map(ElwisNoticeSummary::officialId)
                skippedRows += parsed.skippedNoticeRows
                additionalPagesLoaded += 1
                parsed.pageNumbers
                    .filter { it > 0 && it !in loadedPages && it !in pendingPages }
                    .sorted()
                    .forEach(pendingPages::addLast)
            }
            if (pendingPages.isNotEmpty()) allPagesLoaded = false
            if (summaries.isEmpty() && !isRecognizedEmptyResult(firstPageHtml)) {
                throw ElwisParseException("Die ELWIS-Ergebnisliste konnte nicht verarbeitet werden.")
            }
            val warnings =
                summaries
                    .distinctBy(ElwisNoticeSummary::officialId)
                    .map { summary -> ElwisHtmlParser.summaryToWarning(summary, now, date) }
            val complete = allPagesLoaded && skippedRows == 0
            WarningSourceSnapshot(
                source = source,
                warnings = warnings,
                fetchedAt = now,
                isComplete = complete,
                issue =
                    if (complete) {
                        null
                    } else {
                        "Ein Teil der amtlichen ELWIS-Liste konnte nicht verarbeitet werden."
                    },
            )
        }.fold(
            onSuccess = { snapshot -> WarningSourceResult.Success(snapshot) },
            onFailure = { error ->
                WarningSourceResult.Failure(
                    source = source,
                    message =
                        when (error) {
                            is ElwisParseException -> error.message.orEmpty()
                            is IOException -> "ELWIS ist vorübergehend nicht erreichbar."
                            else -> "Die ELWIS-Meldungen konnten nicht geladen werden."
                        },
                    cause = error,
                )
            },
        )

    override suspend fun fetchDetails(warning: NorthSeaWarning): Result<NorthSeaWarning> =
        runCatching {
            require(warning.source == source) { "Meldung stammt nicht von ELWIS." }
            val validatedUrl =
                OfficialUrlPolicy.validatedUrl(warning.sourceUrl)
                    ?: throw SecurityException("Nicht freigegebene Quellen-URL.")
            val fetchedAt = Instant.now()
            val html = gateway.get(validatedUrl.toString())
            ElwisHtmlParser.parseDetailPage(html, warning, fetchedAt)
                ?: throw ElwisParseException("Die ELWIS-Detailseite konnte nicht verarbeitet werden.")
        }

    private fun isRecognizedEmptyResult(html: String): Boolean {
        val normalized = html.lowercase()
        return listOf(
            "keine bekanntmachungen",
            "keine bfs gefunden",
            "keine treffer",
        ).any(normalized::contains)
    }

    companion object {
        private const val ELWIS_BASE_URL = "https://www.elwis.de"
        private const val ELWIS_SEARCH_URL = "$ELWIS_BASE_URL/DE/dynamisch/Bfs/bfsSeeregion:1"
        private const val ELWIS_LIST_URL =
            "$ELWIS_BASE_URL/DE/dynamisch/Bfs/bfsList:1:bfsNr:DESC:1:elwis_bfs_showBfs"
        private const val MAX_ADDITIONAL_PAGES = 24
        private val BERLIN_ZONE = ZoneId.of("Europe/Berlin")
        private val DATE_PARTS_FORMATTER = DateTimeFormatter.ofPattern("dd|MM|uuuu")

        val ELWIS_NORTH_SEA_DOCUMENT =
            OfficialWarningDocument(
                id = "elwis-bfs-north-sea",
                title = "WSV / ELWIS Bekanntmachungen für Seefahrer",
                issuer = "Wasserstraßen- und Schifffahrtsverwaltung des Bundes",
                description = "Amtliche Bekanntmachungen für die Seeregion Deutschland · Nordsee.",
                url = ELWIS_SEARCH_URL,
                linkKind = OfficialLinkKind.WEB,
            )
    }
}

interface ElwisHttpGateway {
    suspend fun get(url: String): String

    suspend fun postForm(
        url: String,
        fields: Map<String, String>,
    ): String
}

class OkHttpElwisGateway(
    private val client: OkHttpClient = defaultElwisClient(),
) : ElwisHttpGateway {
    override suspend fun get(url: String): String =
        execute(Request.Builder().url(url).get().build())

    override suspend fun postForm(
        url: String,
        fields: Map<String, String>,
    ): String {
        val body = FormBody.Builder().apply { fields.forEach(::add) }.build()
        return execute(Request.Builder().url(url).post(body).build())
    }

    private suspend fun execute(request: Request): String =
        suspendCancellableCoroutine { continuation ->
            val call =
                client.newCall(
                    request
                        .newBuilder()
                        .header("User-Agent", "TideNode-Android/1.0")
                        .header("Accept", "text/html,application/xhtml+xml")
                        .build(),
                )
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(e))
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        val result =
                            runCatching {
                                response.use {
                                    if (!it.isSuccessful) {
                                        throw IOException("ELWIS HTTP ${it.code}")
                                    }
                                    it.body?.string() ?: throw IOException("Leere ELWIS-Antwort")
                                }
                            }
                        if (continuation.isActive) {
                            continuation.resumeWith(result)
                        }
                    }
                },
            )
        }

    companion object {
        private fun defaultElwisClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .cookieJar(InMemoryCookieJar())
                .addNetworkInterceptor { chain ->
                    val requestUrl = chain.request().url
                    if (
                        requestUrl.scheme != "https" ||
                        requestUrl.host != "www.elwis.de" ||
                        requestUrl.port != 443
                    ) {
                        throw IOException("Nicht freigegebene ELWIS-Weiterleitung")
                    }
                    chain.proceed(chain.request())
                }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
    }
}

private class InMemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        cookies.forEach { incoming ->
            this.cookies.removeAll { stored ->
                stored.name == incoming.name &&
                    stored.domain == incoming.domain &&
                    stored.path == incoming.path
            }
            this.cookies += incoming
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return cookies.filter { it.matches(url) }
    }
}

private class ElwisParseException(
    message: String,
) : IllegalStateException(message)
