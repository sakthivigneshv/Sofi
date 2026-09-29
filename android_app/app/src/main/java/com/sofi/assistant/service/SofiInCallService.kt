package com.sofi.assistant.service

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

class SofiInCallService : InCallService() {

    companion object {
        var activeCall: Call? = null
            private set

        fun disconnectCall() {
            try {
                activeCall?.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCall = call

        try {
            // Set audio route to speaker so microphone captures and transmits speech to caller
            setAudioRoute(CallAudioState.ROUTE_SPEAKER)

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.isSpeakerphoneOn = true

            val maxCallVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxCallVol, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVol, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (activeCall == call) {
            activeCall = null
        }
    }
}
