package com.music.musicflame.together

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.music.musicflame.R

/**
 * Notificación del sistema para "a tu amigo le falta esta canción, ¿permites pasársela?".
 * Se muestra al propietario que puso la canción, también en la pantalla de bloqueo,
 * con dos botones: Permitir (sube el archivo) y Negar (no se vuelve a preguntar por esa canción).
 * El diálogo dentro de la app sigue existiendo; responder uno cierra el otro.
 */
object TogetherNotifications {
    const val CHANNEL_ID = "together_upload_requests"
    const val ACTION_ALLOW = "com.music.musicflame.together.ACTION_UPLOAD_ALLOW"
    const val ACTION_DENY = "com.music.musicflame.together.ACTION_UPLOAD_DENY"
    private const val NOTIF_ID = 7301

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            ctx.getString(R.string.tgn_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = ctx.getString(R.string.tgn_channel_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    private fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    private fun actionIntent(ctx: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, requestCode,
            Intent(ctx, TogetherUploadReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    @SuppressLint("MissingPermission") // se comprueba en canNotify()
    fun showUploadPrompt(ctx: Context, p: TogetherManager.UploadPrompt) {
        if (!canNotify(ctx)) return // sin permiso de notificaciones queda solo el diálogo dentro de la app
        ensureChannel(ctx)

        val names = p.who.joinToString(", ")
        val song = if (p.artist.isBlank()) p.title else "${p.title} – ${p.artist}"
        val mb = "%.1f".format(p.sizeBytes / 1_048_576.0)

        val title: String
        val text: String
        if (p.tooBig) {
            title = ctx.getString(R.string.tg_too_big_title)
            text = ctx.getString(R.string.tg_too_big_text, names, song, mb)
        } else {
            title = ctx.getString(if (p.who.size == 1) R.string.tg_friend_missing_one else R.string.tg_friend_missing_many)
            text = ctx.getString(R.string.tg_upload_text, names, song, mb)
        }

        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_together_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
        if (open != null) builder.setContentIntent(open)

        if (p.tooBig) {
            builder.addAction(0, ctx.getString(R.string.tg_got_it), actionIntent(ctx, ACTION_DENY, 2))
        } else {
            builder.addAction(0, ctx.getString(R.string.tgn_allow), actionIntent(ctx, ACTION_ALLOW, 1))
            builder.addAction(0, ctx.getString(R.string.tgn_deny), actionIntent(ctx, ACTION_DENY, 2))
        }
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, builder.build())
    }

    fun cancelUploadPrompt(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
    }

    // ---------------- Chat de la sala ----------------
    const val CHAT_CHANNEL_ID = "together_chat"
    /** Extra del intent de la notificación: al abrir la app, ir directo al chat de la sala. */
    const val EXTRA_OPEN_CHAT = "together_open_chat"
    private const val CHAT_NOTIF_ID = 7302

    private fun ensureChatChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHAT_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHAT_CHANNEL_ID,
            ctx.getString(R.string.tgc_notif_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = ctx.getString(R.string.tgc_notif_channel_desc)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(channel)
    }

    /** Aviso de mensajes nuevos del chat (una sola notificación que se va actualizando con los últimos [lines]). */
    @SuppressLint("MissingPermission") // se comprueba en canNotify()
    fun showChat(ctx: Context, count: Int, lines: List<String>) {
        if (lines.isEmpty() || !canNotify(ctx)) return
        ensureChatChannel(ctx)
        val title = if (count <= 1) ctx.getString(R.string.tgc_notif_title_one)
        else ctx.getString(R.string.tgc_notif_title_many, count)

        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            it.putExtra(EXTRA_OPEN_CHAT, true)
            PendingIntent.getActivity(ctx, 3, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val builder = NotificationCompat.Builder(ctx, CHAT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_together_notif)
            .setContentTitle(title)
            .setContentText(lines.last())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
        if (open != null) builder.setContentIntent(open)
        NotificationManagerCompat.from(ctx).notify(CHAT_NOTIF_ID, builder.build())
    }

    fun cancelChat(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(CHAT_NOTIF_ID)
    }
}

/** Recibe los botones Permitir / Negar de la notificación. */
class TogetherUploadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = TogetherManager.current
        when (intent.action) {
            TogetherNotifications.ACTION_ALLOW -> manager?.confirmUpload()
            TogetherNotifications.ACTION_DENY -> manager?.dismissUploadPrompt()
        }
        TogetherNotifications.cancelUploadPrompt(context)
    }
}
