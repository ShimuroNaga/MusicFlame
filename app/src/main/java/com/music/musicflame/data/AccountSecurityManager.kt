package com.music.musicflame.data

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth

/**
 * Centraliza la lógica de seguridad de cuenta:
 *  - Cerrar sesión (Google + Firebase)
 *  - Borrar cuenta (revoca acceso Google + elimina usuario Firebase)
 */
object AccountSecurityManager {

    // -------------------------------------------------------------------------
    // Cerrar sesión
    // -------------------------------------------------------------------------

    /**
     * Cierra sesión en Google Sign-In Y en Firebase Auth (si había usuario).
     * Llama [onDone] cuando termina (siempre en el hilo principal).
     */
    fun signOut(context: Context, onDone: () -> Unit) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        val client = GoogleSignIn.getClient(context, gso)
        FirebaseAuth.getInstance().signOut()
        client.signOut().addOnCompleteListener { onDone() }
    }

    // -------------------------------------------------------------------------
    // Borrar cuenta
    // -------------------------------------------------------------------------

    /**
     * Revoca el token de Google (impide reconectar sin re-autorizar) y elimina
     * el usuario de Firebase Auth si existe.
     * [onSuccess] si todo salió bien, [onError] con el mensaje de error si algo falló.
     */
    fun deleteAccount(
        context: Context,
        webClientId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context, gso)

        // Paso 1: revocar acceso a Google
        client.revokeAccess().addOnCompleteListener { revokeTask ->
            // Paso 2: eliminar usuario de Firebase (si lo hay)
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            if (firebaseUser != null) {
                firebaseUser.delete().addOnCompleteListener { deleteTask ->
                    if (deleteTask.isSuccessful) {
                        onSuccess()
                    } else {
                        // Si requiere re-autenticación reciente, Firebase lanza
                        // FirebaseAuthRecentLoginRequiredException. Para simplificar,
                        // reportamos el error para que MainActivity lo maneje.
                        onError(deleteTask.exception?.localizedMessage ?: "Error al borrar cuenta")
                    }
                }
            } else {
                // Sin usuario Firebase, con revocar acceso Google es suficiente
                if (revokeTask.isSuccessful) onSuccess()
                else onError(revokeTask.exception?.localizedMessage ?: "Error al revocar acceso")
            }
        }
    }
}
