package no.slomic.smarthytte.vehicletrips.jlr

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import no.slomic.smarthytte.plugins.SignRequest
import java.io.IOException
import kotlin.time.Instant

private const val API_BASE_URL = "https://data.jlr-vcdp.com"

/** The API allows at most 100 journeys per page. */
private const val PAGE_SIZE = 100

/** A stop for a server that keeps sending pages: 1000 pages are 100 000 journeys, far more than a period holds. */
private const val MAX_PAGES = 1000

/**
 * Reads the journeys of the vehicle from the Data Hub REST API, all pages of a period. Every request carries
 * `Authorization: DPoP <token>` and a DPoP proof made for that request, so a retry by the HTTP client gets a new proof
 * (see [SignRequest]).
 *
 * Two answers are handled here, once each per page: the API asks for a DPoP nonce (`use_dpop_nonce`), and the API
 * rejects the access token with 403 `forbidden`, then the token is renewed through [JlrAuthService]. Everything else
 * that is not 200 throws [JlrRequestException]. The messages never contain tokens or the VIN.
 */
class JlrJourneyClient(private val httpClient: HttpClient, private val authService: JlrAuthService, vin: String) {
    private val journeysUrl = "$API_BASE_URL/v1/vehicles/$vin/journeys"

    /**
     * All journeys that ended in the period, oldest page first. [to] left out means up to now.
     *
     * @throws JlrAuthException when the login does not work, see [JlrAuthService]
     * @throws JlrRequestException when the API fails or answers something unexpected
     */
    suspend fun fetchJourneys(from: Instant, to: Instant? = null): List<JlrJourneyResponse> {
        val journeys = mutableListOf<JlrJourneyResponse>()
        var pageIndex = 0
        do {
            if (pageIndex >= MAX_PAGES) throw JlrRequestException("JLR journeys has more than $MAX_PAGES pages")
            val page = fetchPage(from, to, pageIndex)
            journeys += page.items
            pageIndex++
            // An empty page ends the loop too, whatever totalPages says
        } while (pageIndex < page.page.totalPages && page.items.isNotEmpty())
        return journeys
    }

    private suspend fun fetchPage(from: Instant, to: Instant?, pageIndex: Int): JlrJourneyListResponse = try {
        val query = listOfNotNull(
            "page" to pageIndex.toString(),
            "size" to PAGE_SIZE.toString(),
            "from" to from.toString(),
            to?.let { "to" to it.toString() },
        )
        var token = authService.accessToken()
        var response = getWithNonceRetry(token, query)
        if (response.isRejectedToken()) {
            token = authService.accessTokenAfterRejection(token)
            response = getWithNonceRetry(token, query)
        }
        if (response.status != HttpStatusCode.OK) {
            throw JlrRequestException("JLR journeys request failed: status=${response.status.value}")
        }
        response.decodeBody("journeys")
    } catch (e: IOException) {
        // A network failure, also while the body is read, or in the login. Not the exception itself as cause: a Ktor
        // message contains the URL, and so the VIN
        throw JlrRequestException("JLR journeys request failed: ${e::class.simpleName ?: "IOException"}")
    }

    /** One request, and one more with the nonce when the API answers `use_dpop_nonce`. */
    private suspend fun getWithNonceRetry(token: String, query: List<Pair<String, String>>): HttpResponse {
        val first = get(token, query)
        return if (first.isNonceChallenge()) {
            authService.rememberDpopNonce(first.headers[DPOP_NONCE_HEADER].orEmpty())
            get(token, query)
        } else {
            first
        }
    }

    private suspend fun get(token: String, query: List<Pair<String, String>>): HttpResponse =
        httpClient.get(journeysUrl) {
            query.forEach { (name, value) -> parameter(name, value) }
            val sign: (HttpRequestBuilder) -> Unit = { request ->
                // Set, not append: on a retry the old values are still on the request
                request.headers[HttpHeaders.Authorization] = "DPoP $token"
                request.headers[DPOP_HEADER] = authService.apiDpopProof("GET", journeysUrl, token)
            }
            attributes.put(SignRequest, sign)
            sign(this)
        }

    private fun HttpResponse.isNonceChallenge(): Boolean = headers[DPOP_NONCE_HEADER] != null &&
        status == HttpStatusCode.Unauthorized &&
        headers[HttpHeaders.WWWAuthenticate].orEmpty().contains("use_dpop_nonce")

    private suspend fun HttpResponse.isRejectedToken(): Boolean =
        status == HttpStatusCode.Forbidden && errorCode() == "forbidden"
}
