/*
 * OpenSchoolCloud Calendar
 * Copyright (C) 2025 OpenSchoolCloud / Aldewereld Consultancy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package nl.openschoolcloud.calendar.data.remote.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import nl.openschoolcloud.calendar.BuildConfig
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OidcIdentity(val sub: String, val email: String?)

/**
 * Drives the OIDC Authorization Code + PKCE flow against Authentik using a
 * Custom Tab. Password entry and MFA happen entirely in the browser tab; this
 * class only ever handles the resulting tokens.
 */
class AuthManager(
    private val context: Context,
    private val tokenStore: OidcTokenStore
) {
    // Populated lazily by [serviceConfig] via OIDC discovery, then reused.
    // Authentik's real authorize/token endpoints are global (not per-app),
    // so we discover them from the issuer's well-known document instead of
    // hardcoding paths.
    private var discoveredServiceConfig: AuthorizationServiceConfiguration? = null

    val isLoggedIn: Boolean get() = tokenStore.isLoggedIn

    private suspend fun serviceConfig(): AuthorizationServiceConfiguration {
        discoveredServiceConfig?.let { return it }
        return suspendCancellableCoroutine { cont ->
            AuthorizationServiceConfiguration.fetchFromIssuer(
                Uri.parse(BuildConfig.OIDC_ISSUER)
            ) { config, ex ->
                if (config != null) {
                    discoveredServiceConfig = config
                    cont.resume(config)
                } else {
                    cont.resumeWithException(ex ?: IllegalStateException("OIDC discovery failed"))
                }
            }
        }
    }

    suspend fun buildAuthRequestIntent(): Intent {
        val config = serviceConfig()
        val service = AuthorizationService(context)
        val request = AuthorizationRequest.Builder(
            config,
            BuildConfig.OIDC_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(BuildConfig.OIDC_REDIRECT_SCHEME + "://oidc")
        ).setScopes("openid", "profile", "email", "offline_access").build()
        val intent = service.getAuthorizationRequestIntent(request)
        service.dispose()
        return intent
    }

    suspend fun handleAuthResponse(data: Intent): Result<OidcIdentity> {
        val response = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        if (response == null) {
            return Result.failure(ex ?: IllegalStateException("No authorization response"))
        }
        val service = AuthorizationService(context)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { service.dispose() }
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenEx ->
                if (tokenResponse == null) {
                    service.dispose()
                    cont.resume(Result.failure(tokenEx ?: IllegalStateException("Token exchange failed")))
                    return@performTokenRequest
                }
                val authState = AuthState(response, tokenResponse, null)
                tokenStore.authStateJson = authState.jsonSerializeString()
                val idToken = tokenResponse.idToken
                val identity = parseIdentity(idToken)
                service.dispose()
                cont.resume(Result.success(identity))
            }
        }
    }

    fun logout() = tokenStore.clear()

    /**
     * A fresh, valid OIDC access token, or null if not logged in / refresh
     * failed. Access tokens live only 5 minutes (refresh tokens 30 days), so
     * this always goes through AppAuth's [AuthState.performActionWithFreshTokens],
     * which transparently refreshes using the stored refresh token when the
     * access token has expired. The (possibly updated) AuthState is persisted
     * back to [tokenStore]. Used only as a bearer header value — never logged.
     */
    suspend fun freshAccessToken(): String? {
        val json = tokenStore.authStateJson ?: return null
        val authState = try {
            AuthState.jsonDeserialize(json)
        } catch (e: Exception) {
            return null
        }
        val service = AuthorizationService(context)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { service.dispose() }
            authState.performActionWithFreshTokens(service) { accessToken, _, ex ->
                tokenStore.authStateJson = authState.jsonSerializeString()
                service.dispose()
                if (ex != null) cont.resume(null) else cont.resume(accessToken)
            }
        }
    }

    /** Extract sub + email from the ID token payload (base64url middle segment). */
    private fun parseIdentity(idToken: String?): OidcIdentity {
        if (idToken == null) return OidcIdentity(sub = "", email = null)
        return try {
            val payload = idToken.split(".")[1]
            val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING))
            val obj = org.json.JSONObject(json)
            OidcIdentity(sub = obj.optString("sub"), email = obj.optString("email").ifEmpty { null })
        } catch (e: Exception) {
            OidcIdentity(sub = "", email = null)
        }
    }
}
