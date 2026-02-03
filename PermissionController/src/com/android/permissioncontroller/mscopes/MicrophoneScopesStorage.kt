package com.android.permissioncontroller.mscopes

import android.content.pm.GosPackageState
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Storage for microphone scopes configuration.
 * Stores the audio file URI and resolved filesystem path.
 */
class MicrophoneScopesStorage {
    companion object {
        private const val TAG = "MicrophoneScopesStorage"

        fun deserialize(ps: GosPackageState): MicrophoneScopesStorage {
            val data = ps.microphoneScopes
            if (data == null || data.isEmpty()) {
                return MicrophoneScopesStorage()
            }

            return try {
                ByteArrayInputStream(data).use { bais ->
                    DataInputStream(bais).use { dis ->
                        val storage = MicrophoneScopesStorage()
                        storage.audioFilePath = dis.readUTF()
                        try {
                            storage.resolvedFilePath = dis.readUTF()
                        } catch (_: Exception) {
                            // old format without resolved path
                        }
                        storage
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deserialize microphone scopes", e)
                MicrophoneScopesStorage()
            }
        }

        fun isEmpty(ps: GosPackageState): Boolean {
            val data = ps.microphoneScopes
            return data == null || data.isEmpty()
        }
    }

    var audioFilePath: String? = null
        private set

    var resolvedFilePath: String? = null
        private set

    fun setAudioFilePath(path: String?) {
        this.audioFilePath = path
    }

    fun setResolvedFilePath(path: String?) {
        this.resolvedFilePath = path
    }

    fun serialize(): ByteArray? {
        if (audioFilePath == null) {
            return null
        }
        return try {
            ByteArrayOutputStream().use { baos ->
                DataOutputStream(baos).use { dos ->
                    dos.writeUTF(audioFilePath!!)
                    if (resolvedFilePath != null) {
                        dos.writeUTF(resolvedFilePath!!)
                    }
                }
                baos.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize microphone scopes", e)
            null
        }
    }
}
