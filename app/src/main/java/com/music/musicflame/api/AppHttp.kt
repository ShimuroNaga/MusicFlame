package com.music.musicflame.api

import android.content.Context
import android.content.pm.PackageManager
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP único de la app (OkHttp): reutiliza conexiones, tiene timeouts explícitos y una caché en disco
 * de 10 MB (cacheDir/http). Lo usan Retrofit (YouTube), el chequeo de actualizaciones, Ko-fi y la meta de ahorro.
 *
 * Las descargas grandes (modelos de IA / AutoMix / wake) y los streams (Groq) NO pasan por aquí a propósito.
 */
object AppHttp {
    private const val CACHE_BYTES = 10L * 1024 * 1024
    private const val YT_HOST = "www.googleapis.com"

    /** TTL con el que se cachean en disco las respuestas públicas de YouTube (aunque Google no lo pida). */
    private const val TTL_SEARCH_S = 15 * 60
    private const val TTL_TRENDING_S = 60 * 60

    @Volatile private var appContext: Context? = null

    /** Llamar una vez en Application.onCreate. Si no se llama, el cliente funciona igual pero sin caché. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val client: OkHttpClient by lazy {
        val b = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
        appContext?.let { ctx ->
            b.cache(Cache(File(ctx.cacheDir, "http"), CACHE_BYTES))
            b.addInterceptor(AndroidKeyInterceptor(ctx))
        }
        b.addNetworkInterceptor(YouTubeCacheInterceptor)
        b.build()
    }

    /**
     * Si la API key de YouTube se restringe en Google Cloud a "Aplicaciones Android" (paquete + SHA-1), las
     * peticiones REST deben llevar estas dos cabeceras. El SHA-1 se calcula de la firma real del APK instalado,
     * así que sirve igual para debug y release (los dos SHA-1 deben estar dados de alta en la clave).
     */
    private class AndroidKeyInterceptor(private val ctx: Context) : Interceptor {
        private val certSha1: String? by lazy {
            try {
                @Suppress("DEPRECATION")
                val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val sig = info.signingInfo?.apkContentsSigners?.firstOrNull()
                sig?.let { MessageDigest.getInstance("SHA-1").digest(it.toByteArray()).joinToString("") { b -> "%02X".format(b) } }
            } catch (_: Exception) { null }
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            if (req.url.host != YT_HOST) return chain.proceed(req)
            val nb = req.newBuilder().header("X-Android-Package", ctx.packageName)
            certSha1?.let { nb.header("X-Android-Cert", it) }
            return chain.proceed(nb.build())
        }
    }

    /**
     * Pone Cache-Control a las respuestas PÚBLICAS de YouTube (sin Authorization): búsquedas 15 min y tendencias
     * (mostPopular) 1 h. OkHttp las guarda en disco, así que sobreviven al cierre de la app. Las peticiones con
     * cuenta de Google (likes, suscripciones) no se tocan.
     */
    private object YouTubeCacheInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val resp = chain.proceed(req)
            if (req.method != "GET" || !resp.isSuccessful || req.header("Authorization") != null) return resp
            if (req.url.host != YT_HOST) return resp
            val path = req.url.encodedPath
            val ttl = when {
                path.endsWith("/youtube/v3/search") -> TTL_SEARCH_S
                path.endsWith("/youtube/v3/videos") && req.url.queryParameter("chart") == "mostPopular" -> TTL_TRENDING_S
                else -> return resp
            }
            return resp.newBuilder()
                .header("Cache-Control", "public, max-age=$ttl")
                .removeHeader("Pragma")
                .removeHeader("Expires")
                .build()
        }
    }
}
