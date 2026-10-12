# Política de privacidad de MusicFlame

*Última actualización: 11 de octubre de 2026 (versión 3.13)*

[English version below](#privacy-policy-english)

MusicFlame es un reproductor de música para Android, desarrollado de forma independiente por ShimuroNaga. Esta página explica qué datos usa la app, para qué y cómo puedes pedir que se borren. MusicFlame no tiene anuncios, no vende tus datos y no los comparte con terceros para publicidad.

## 1. Lo que se queda en tu celular

La mayor parte de la app funciona solo en tu dispositivo y **no envía nada a ningún servidor**:

- **Tu biblioteca de música.** La app lee los archivos de audio de tu celular (permiso de audio) para mostrarlos y reproducirlos.
- **Tus ajustes y personalizaciones:** tema, fondos, carátulas personalizadas, playlists, favoritos, estadísticas de escucha, ecualizador y cachés. Se guardan en el almacenamiento interno de la app.
- **Copia de seguridad en archivo (.json).** Si exportas tu configuración, el archivo queda donde tú lo guardes.
- **Análisis local de audio** (por ejemplo, para normalizar volumen o armar mezclas): se hace dentro de tu celular y sus resultados no se envían.

Permisos que pide la app y para qué sirven:

| Permiso | Para qué se usa |
|---|---|
| Audio / almacenamiento | Leer tu biblioteca. El "acceso a todos los archivos" es **opcional** y solo sirve para escribir etiquetas reales (carátula, título, artista, álbum) en tus archivos; se hace un respaldo antes de modificar. |
| Micrófono (RECORD_AUDIO) | Solo para el visualizador del ecualizador, que lee la señal de audio que reproduce la propia app. **No se graba ni se envía audio.** |
| Notificaciones | Controles de reproducción y avisos de "En compañía". |
| Internet | Funciones en línea descritas abajo. |
| Ignorar optimización de batería | Que la reproducción no se corte en segundo plano. |

## 2. Funciones opcionales que usan servicios en línea

### Inicio de sesión con Google (opcional)
Si inicias sesión, la app recibe el nombre y el correo de tu cuenta de Google. Se usa para:
- Identificarte en la app.
- Hacer **copias de seguridad en tu Google Drive**. La app solo tiene acceso a los archivos que ella misma crea (permiso `drive.file`), no al resto de tu Drive.
- Entrar a las salas de "En compañía".

### "En compañía" (escuchar juntos)
Usa **Firebase Authentication** y **Firebase Realtime Database** (Google) para las salas, y **Supabase Storage** para compartir archivos de audio. Cuando usas una sala se guardan:
- Un identificador de usuario de Firebase (si no usas Google, se crea uno anónimo).
- El **nombre que elijas mostrar** (hasta 24 caracteres; puede ser libre o el de tu cuenta de Google).
- El código de la sala, quién es anfitrión o propietario y si estás en línea.
- La cola y el estado de reproducción: título, artista, duración, quién agregó cada canción, posición y pausa/reproducción, y los votos para saltar.
- **Archivos de audio** (máximo 25 MB cada uno), solo si un propietario permite pasar una canción a quienes no la tienen. Se guardan con nombre aleatorio y sirven únicamente para que los integrantes de la sala la escuchen.

Cuándo se borra:
- Al salir de una sala se borran tus datos de presencia y tus canciones en espera, y se eliminan los archivos de audio que subiste.
- Si el anfitrión sale o cierra la sala, se borra toda la sala.
- Si la app se cierra de forma inesperada, algunos datos pueden tardar en limpiarse; los archivos subidos que hayan quedado se eliminan la próxima vez que abras la app.

Solo comparte en una sala música que tengas derecho a compartir.

### Firebase Analytics
La app incluye Firebase Analytics (Google), que recopila de forma automática datos de uso y del dispositivo: un identificador de instalación, modelo y versión de Android, país o región aproximada, e información de sesiones. Se usa para entender el uso de la app y detectar fallos. MusicFlame no agrega eventos propios con datos personales.

### YouTube
La búsqueda y reproducción de YouTube usan la API y el reproductor oficiales de YouTube. Al usarlos, tus consultas y tu reproducción están sujetas a los [Términos de YouTube](https://www.youtube.com/t/terms) y a la [Política de privacidad de Google](https://policies.google.com/privacy).

### Compras (Ko-fi)
Las funciones de pago se venden a través de **Ko-fi**, que procesa el pago. MusicFlame **nunca ve ni guarda tus datos de pago**. Cuando pagas, Ko-fi avisa a un servidor de MusicFlame (Supabase) con el correo que usaste y el producto comprado, y ahí se guarda ese registro. Para restaurar tus compras, la app consulta ese servidor con el correo de tu cuenta de Google (o el que escribas) y guarda en tu celular qué productos tienes.

### Meta pública
La app puede leer un archivo público del repositorio de GitHub (`meta.json`) para mostrar una meta de ahorro. Esa petición es una descarga normal de una página pública.

## 3. Con quién se comparten los datos
Solo con los proveedores que hacen funcionar cada función: Google (inicio de sesión, Drive, Firebase, YouTube), Supabase (archivos de las salas y registro de compras), Ko-fi (pagos) y GitHub (archivo público). Cada uno trata los datos según su propia política. No se venden ni se usan para publicidad.

## 4. Cómo borrar tus datos o pedir ayuda
- **Datos en tu celular:** desinstala la app o borra sus datos desde los ajustes de Android.
- **Salas:** salir de la sala borra tus datos de ella (ver arriba).
- **Drive:** puedes borrar los archivos de respaldo desde tu Google Drive y quitar el acceso de la app en https://myaccount.google.com/permissions.
- **Datos guardados en Firebase asociados a tu usuario** (por ejemplo, el identificador de usuario), o cualquier otra duda o solicitud: abre un *issue* en https://github.com/ShimuroNaga/MusicFlame/issues. No incluyas contraseñas ni datos sensibles en el issue; si hace falta, se coordina otro medio de contacto.

## 5. Menores de edad
MusicFlame no está dirigida a menores de 13 años y no recopila a propósito datos de niños. Si crees que un menor usó funciones en línea, avísanos para borrar sus datos.

## 6. Cambios a esta política
Si la app cambia lo que hace con los datos, se actualizará esta página y la fecha de arriba. Los cambios se ven en el historial del repositorio.

---

<a id="privacy-policy-english"></a>

# MusicFlame Privacy Policy

*Last updated: October 11, 2026 (version 3.13)*

MusicFlame is an Android music player independently developed by ShimuroNaga. This page explains what data the app uses, why, and how you can ask for it to be deleted. MusicFlame has no ads, does not sell your data, and does not share it with third parties for advertising.

## 1. What stays on your phone

Most of the app works only on your device and **sends nothing to any server**:

- **Your music library.** The app reads the audio files on your phone (audio permission) to show and play them.
- **Your settings and customizations:** theme, backgrounds, custom cover art, playlists, favorites, listening statistics, equalizer and caches, stored in the app's internal storage.
- **Backup file (.json).** If you export your settings, the file stays wherever you save it.
- **Local audio analysis** (for example, volume normalization or mixes) runs on your phone and the results are not sent anywhere.

Permissions the app requests and why:

| Permission | Purpose |
|---|---|
| Audio / storage | Read your library. "All files access" is **optional** and only used to write real tags (cover, title, artist, album) into your files; a backup is made first. |
| Microphone (RECORD_AUDIO) | Only for the equalizer visualizer, which reads the audio signal the app itself plays. **No audio is recorded or sent.** |
| Notifications | Playback controls and "Together" alerts. |
| Internet | Online features described below. |
| Ignore battery optimization | So playback is not cut off in the background. |

## 2. Optional features that use online services

### Google Sign-In (optional)
If you sign in, the app receives your Google account name and email. It is used to:
- Identify you in the app.
- Make **backups to your Google Drive**. The app can only access files it creates itself (`drive.file` scope), not the rest of your Drive.
- Join "Together" rooms.

### "Together" (listen together)
Uses **Firebase Authentication** and **Firebase Realtime Database** (Google) for rooms, and **Supabase Storage** to share audio files. When you use a room, the following is stored:
- A Firebase user identifier (anonymous if you do not use Google).
- The **display name you choose** (up to 24 characters; can be free text or your Google account name).
- The room code, who the host or owners are, and whether you are online.
- The queue and playback state: title, artist, duration, who added each song, position, play/pause state, and skip votes.
- **Audio files** (max 25 MB each), only if an owner allows sending a song to people who do not have it. They are stored under a random name and exist only so room members can listen.

When it is deleted:
- When you leave a room, your presence data and queued songs are removed, and the audio files you uploaded are deleted.
- If the host leaves or closes the room, the whole room is deleted.
- If the app closes unexpectedly, some data may take a while to clean up; leftover uploaded files are removed the next time you open the app.

Only share music in a room that you have the right to share.

### Firebase Analytics
The app includes Firebase Analytics (Google), which automatically collects usage and device data: an installation identifier, device model and Android version, approximate country or region, and session information. It is used to understand app usage and detect crashes. MusicFlame does not add its own events containing personal data.

### YouTube
YouTube search and playback use the official YouTube API and player. When you use them, your queries and playback are subject to the [YouTube Terms of Service](https://www.youtube.com/t/terms) and the [Google Privacy Policy](https://policies.google.com/privacy).

### Purchases (Ko-fi)
Paid features are sold through **Ko-fi**, which processes the payment. MusicFlame **never sees or stores your payment details**. When you pay, Ko-fi notifies a MusicFlame server (Supabase) with the email you used and the product you bought, and that record is stored there. To restore your purchases, the app queries that server with your Google account email (or the one you type) and stores on your phone which products you own.

### Public goal file
The app may read a public file from the GitHub repository (`meta.json`) to show a savings goal. This is an ordinary download of a public page.

## 3. Who data is shared with
Only with the providers that make each feature work: Google (sign-in, Drive, Firebase, YouTube), Supabase (room files and purchase records), Ko-fi (payments) and GitHub (public file). Each handles data under its own policy. Data is not sold or used for advertising.

## 4. How to delete your data or get help
- **Data on your phone:** uninstall the app or clear its data from Android settings.
- **Rooms:** leaving a room deletes your data from it (see above).
- **Drive:** you can delete backup files from your Google Drive and remove the app's access at https://myaccount.google.com/permissions.
- **Data stored in Firebase tied to your user** (for example, the user identifier), or any other question or request: open an *issue* at https://github.com/ShimuroNaga/MusicFlame/issues. Do not include passwords or sensitive data in the issue; another contact channel can be arranged if needed.

## 5. Children
MusicFlame is not directed to children under 13 and does not knowingly collect their data. If you believe a child used the online features, let us know so their data can be deleted.

## 6. Changes to this policy
If the app changes how it handles data, this page and the date above will be updated. Changes are visible in the repository history.
