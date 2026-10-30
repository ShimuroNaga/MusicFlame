package com.music.musicflame.voice

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService

/**
 * Registra MusicFlame como "asistente digital" del teléfono (Ajustes > Apps > Apps predeterminadas >
 * Asistente digital). Es la ÚNICA forma que Android permite para atender el botón power: cuando el usuario
 * mantiene pulsado el botón de encendido, el sistema abre el asistente elegido. Android decide cuánto hay que
 * mantener pulsado (ajustable en Ajustes del sistema en algunos teléfonos); la app no puede cambiarlo.
 */
class VoiceAssistService : VoiceInteractionService()

class VoiceAssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = VoiceAssistSession(this)
}

class VoiceAssistSession(private val service: VoiceAssistSessionService) : VoiceInteractionSession(service) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        startAssistantActivity(Intent(service, VoiceAssistantActivity::class.java))
        hide()
    }
}

/** Sin función real: Android exige declarar un reconocedor en el servicio de asistente. No se usa. */
class VoiceAssistStubRecognizer : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(android.speech.SpeechRecognizer.ERROR_CLIENT)
    }
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
