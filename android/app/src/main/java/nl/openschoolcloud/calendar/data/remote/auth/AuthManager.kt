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
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(BuildConfig.OIDC_ISSUER + "authorize/"),
        Uri.parse(BuildConfig.OIDC_ISSUER + "token/")
    )

    val isLoggedIn: Boolean get() = tokenStore.isLoggedIn

    fun buildAuthRequestIntent(): Intent {
        val service = AuthorizationService(context)
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            BuildConfig.OIDC_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(BuildConfig.OIDC_REDIRECT_SCHEME + "://oidc")
        ).setScopes("openid", "profile", "email", "offline_access").build()
        return service.getAuthorizationRequestIntent(request)
    }

    suspend fun handleAuthResponse(data: Intent): Result<OidcIdentity> {
        val response = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        if (response == null) {
            return Result.failure(ex ?: IllegalStateException("No authorization response"))
        }
        val service = AuthorizationService(context)
        return suspendCancellableCoroutine { cont ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenEx ->
                if (tokenResponse == null) {
                    cont.resume(Result.failure(tokenEx ?: IllegalStateException("Token exchange failed")))
                    return@performTokenRequest
                }
                val authState = AuthState(response, tokenResponse, null)
                tokenStore.authStateJson = authState.jsonSerializeString()
                val idToken = tokenResponse.idToken
                val identity = parseIdentity(idToken)
                cont.resume(Result.success(identity))
            }
        }
    }

    fun logout() = tokenStore.clear()

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
