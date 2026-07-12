package com.music.musicflame.data

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.api.client.http.FileContent

class DriveRepository(private val context: Context) {

    // 1. Inicializar el motor de Drive usando la cuenta de Google activa
    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("MusicFlame")
            .build()
    }

    // 2. Buscar nuestra base de operaciones (La carpeta)
    suspend fun getOrCreateAppFolder(account: GoogleSignInAccount): String? = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService(account)
            val folderName = "MusicFlame"

            val query = "mimeType='application/vnd.google-apps.folder' and name='$folderName' and trashed=false"
            val fileList = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            if (fileList.files.isNotEmpty()) {
                return@withContext fileList.files[0].id
            }

            val folderMetadata = File().apply {
                name = folderName
                mimeType = "application/vnd.google-apps.folder"
            }

            val folder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute()

            return@withContext folder.id
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    // 3. El escáner de música
    suspend fun getSongsFromFolder(account: GoogleSignInAccount, folderId: String): List<File> = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService(account)

            val query = "'$folderId' in parents and mimeType contains 'audio/' and trashed=false"
            val fileList = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, size, webContentLink)")
                .execute()

            return@withContext fileList.files ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    // 4. Función para subir una canción local a nuestra carpeta de Drive
    suspend fun uploadSong(account: GoogleSignInAccount, folderId: String, localFilePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService(account)
            val fileToUpload = java.io.File(localFilePath)

            // Verificamos que el archivo físico realmente exista en el teléfono
            if (!fileToUpload.exists()) return@withContext false

            // Metadatos: Le decimos a Drive cómo se llama el archivo y en qué carpeta guardarlo
            val fileMetadata = File().apply {
                name = fileToUpload.name
                parents = listOf(folderId) // Aquí usamos el ID de la carpeta "MusicFlame"
            }

            // El contenido real (los bytes del audio mp3)
            val mediaContent = FileContent("audio/mpeg", fileToUpload)

            // Ejecutamos la subida a los servidores de Google
            driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()

            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}