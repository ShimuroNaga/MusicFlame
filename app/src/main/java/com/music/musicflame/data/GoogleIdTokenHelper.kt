package com.music.musicflame.data

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pide a Google un ID token de una cuenta del dispositivo SIN cambiar la sesion de la app.
 * Es lo que el servidor (Edge Function "kofi-restore") verifica para saber que de verdad
 * eres dueno de ese correo.
 */
object GoogleIdTokenHelper {
    // Mismo Web client ID que usa MainActivity (es publico).
    private const val WEB_CLIENT_ID = "176181653925-etnugbpe1mqhu1gl3lu1njbu9iihcn1k.apps.googleusercontent.com"

    sealed class Result {
        data class Ok(val token: String) : Result()
        /** Google pide un permiso la primera vez: lanzar [intent] y reintentar. */
        data class NeedsConsent(val intent: Intent) : Result()
        object Failure : Result()
    }

    suspend fun get(context: Context, email: String): Result = withContext(Dispatchers.IO) {
        try {
            val account = Account(email.trim(), "com.google")
            val token = GoogleAuthUtil.getToken(context.applicationContext, account, "audience:server:client_id:$WEB_CLIENT_ID")
            if (token.isNullOrBlank()) Result.Failure else Result.Ok(token)
        } catch (e: UserRecoverableAuthException) {
            e.intent?.let { Result.NeedsConsent(it) } ?: Result.Failure
        } catch (e: Exception) {
            Result.Failure
        }
    }
}
