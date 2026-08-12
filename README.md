# MusicFlame 🔥🎵

![Estado del Proyecto](https://img.shields.io/badge/Estado-En_Desarrollo_Activo-brightgreen)
![Plataforma](https://img.shields.io/badge/Plataforma-Android-blue)

**MusicFlame** es un reproductor de música versátil y moderno que combina tu biblioteca local con el extenso catálogo de YouTube en una sola aplicación. Diseñado con un enfoque en la personalización, la estética y el rendimiento.

---

## ✨ Características Principales

### 🎵 Gestión de Música y Reproducción
*   **Buscador Dual:** Encuentra canciones localmente en tu dispositivo o busca en línea a través de YouTube.
*   **Tu Mix Diario:** Generación diaria y aleatoria de un mix de canciones (con cantidad y duración total visibles) listo para reproducir o guardar como playlist. Se bloquea tras generarse e indica cuántas horas faltan para el siguiente.
*   **Filtros Inteligentes:** Ordena tu biblioteca por fecha, A-Z o Z-A. Filtra audios por duración para evitar reproducir notas de voz o sonidos cortos.
*   **Selección Múltiple:** Selecciona canciones o playlists manteniendo pulsado o activando el modo selección con un botón (incluye "Seleccionar todos").
*   **Control Único de Reproducción:** Un solo botón cíclico (en el mini reproductor, la notificación y el reproductor a pantalla completa) que alterna entre Normal → Aleatorio → Repetir Todo → Repetir Una.
*   **Letras Sincronizadas:** Búsqueda automática de letras (LRC) en línea, con animación línea por línea, velocidad y tipo de animación (Deslizar/Desvanecer/Rebote) ajustables. Si no se encuentra automáticamente, se puede verificar manualmente vía YouTube o insertar la letra a mano.
*   **Visualizador de Audio Real:** 32 barras de espectro conectadas al audio que realmente está sonando (no una animación genérica), con auto-ganancia y detección de golpe de graves para que se sienta viva y sincronizada con la canción.
*   **Audio Avanzado:** Ecualizador de 5 bandas con presets, Bass Boost, Virtualizer, Loudness Enhancer y Reverb, además de optimización de batería para evitar interrupciones en segundo plano.

### 💿 Álbumes
*   Explora tu biblioteca agrupada por álbum en una cuadrícula con carátulas.
*   Entra al detalle de un álbum para reproducirlo completo, reordenarlo (orden original / A-Z / Z-A) o **exportarlo como playlist M3U**.

### 🗂️ Playlists y Papelera
*   Crea playlists y agrega canciones desde cualquier pantalla.
*   **Papelera de Reciclaje:** Las canciones **y las playlists** eliminadas se almacenan temporalmente y se borran automáticamente tras 30 días (con opción de borrado permanente manual o restauración). Al eliminar una playlist, las canciones dentro de ella nunca se borran del dispositivo.

### ☁️ Respaldo en Google Drive
*   Inicia sesión con tu cuenta de Google para subir canciones a una carpeta dedicada de la app dentro de tu Drive, como respaldo fuera del dispositivo.

### 🎨 Personalización Profunda
*   **Temas Visuales:** Adaptable al sistema (Material 3 / Material You), Modo Claro, Modo Oscuro y **Modo AMOLED** (para ahorro máximo de batería).
*   **Fondos Personalizados:** Soporte para añadir imágenes o GIFs al fondo de la app, con control manual de brillo.
*   **Color de Texto:** Blanco o negro, tanto a nivel global de la app como específicamente para la letra sincronizada.
*   **Forma de Carátula:** Cuadrado, círculo, hexágono, vinilo o "squircle" en toda la app.
*   **Iconos Alternativos:** Varios diseños de ícono de app para elegir (Original, Brillante, Pixelart, Cookies N' Cream, Escala de grises, RemixFlame).
*   **Interfaz:** Bordes redondeados activables/desactivables, cantidad de columnas en la cuadrícula de álbumes (2 o 4), y opacidad del widget de pantalla de inicio ajustable.
*   **Barras del Ecualizador Adaptativas:** Su color (blanco o negro) se ajusta automáticamente según lo que haya detrás — nunca hay que elegirlo a mano.

### 🎧 Widget de Pantalla de Inicio
*   Control rápido con carátula, Play/Pausa, Siguiente y Anterior directo desde el home screen, sin abrir la app.

### 🔄 Actualizaciones
*   Aviso dentro de la app cuando hay una versión nueva disponible, con opción de ignorar una versión específica y no volver a verla.

### 🚀 Primer Uso Guiado
*   Onboarding paso a paso que cubre permisos, importación de canciones, apariencia, cuenta y letras, además de la invitación a la comunidad.

---

## 🤖 ¿Y la IA (Gemini)?

Versiones anteriores de MusicFlame incluían integración con Gemini para chatear y pedir recomendaciones musicales. **Esa función ya no está disponible.**

El motivo es simple: para sostenerla de forma segura se necesitaba un backend en Firebase, y los planes que lo permiten con el volumen de uso de la app requieren pago — algo que no me es sostenible mantener para un proyecto personal y gratuito.

En su lugar, ese espacio se aprovechó para construir la nueva pestaña de **Álbumes** y otras funciones nativas (exportar a M3U, respaldo en Google Drive, letras sincronizadas, visualizador de audio real, etc.) que no dependen de ningún servidor de pago y funcionan 100% offline salvo cuando específicamente necesitan internet (buscar en YouTube, buscar letras, o subir a Drive).

No está descartado revisar una alternativa a futuro, pero por ahora todo el desarrollo se enfoca en pulir lo que la app ya hace sin depender de servicios de pago.

---

## 🚀 Instalación

Descargar e instalar MusicFlame es muy sencillo. No necesitas compilar el código si solo quieres probar la app:

1. Ve a la sección de [Releases](../../releases) de este repositorio.
2. Busca la versión más reciente.
3. Desplázate hacia abajo hasta el apartado **Assets** y descarga el archivo `.apk` (ej. `MusicFlame-v1.5.apk`).
4. Abre el archivo en tu dispositivo Android. *(Es posible que debas conceder permisos para instalar aplicaciones de orígenes desconocidos).*
5. ¡Listo! Abre la app, concede los permisos de lectura de audio y disfruta.

---

## 🛠️ Aviso de Desarrollo

> **⚠️ App en construcción continua**
> MusicFlame es un proyecto en desarrollo activo. La sección de IA (Gemini) fue retirada y reemplazada por la nueva pestaña de **Álbumes** y otras funciones nativas, tal como se explica arriba.
>
> **📌 Nota:** Esta ha sido una actualización grande (nueva sección de Álbumes, papelera de playlists, respaldo en Drive, letras sincronizadas, visualizador de audio real, personalización avanzada, etc.), por lo que nos tomaremos un tiempo para pulir todo bien. Si notas algo extraño o un *bug*, por favor repórtalo. ¡Gracias por la paciencia!

---

## 👥 Colaboradores y Testers

Gracias a estas increíbles personas por sus contribuciones en el control de calidad, *testing* y reporte de errores. ¡Su ayuda es fundamental para que MusicFlame funcione al 100%!

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="14.28%">
        <a href="https://github.com/naofresita18">
          <img src="https://github.com/naofresita18.png" width="100px;" alt="naofresita18"/><br />
          <sub><b>naofresita18</b></sub>
        </a><br />
        <a href="#testing-naofresita18" title="Testing">🧪</a> 
        <a href="#bug-naofresita18" title="Bug reports">🐛</a>
      </td>
      <td align="center" valign="top" width="14.28%">
        <a href="https://github.com/deivid-boop">
          <img src="https://github.com/deivid-boop.png" width="100px;" alt="deivid-boop"/><br />
          <sub><b>deivid-boop</b></sub>
        </a><br />
        <a href="#testing-deivid-boop" title="Testing">🧪</a> 
        <a href="#bug-deivid-boop" title="Bug reports">🐛</a>
      </td>
    </tr>
  </tbody>
</table>
<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->
<!-- ALL-CONTRIBUTORS-LIST:END -->

---

## 🤝 Comunidad y Contacto

¡Las sugerencias y reportes son bienvenidos! 

*   **Desarrollador Principal:** ShimuroNaga
*   **Correo de contacto / Reporte de bugs:** oomo87284@gmail.com

### 👾 Servidor de Discord
Tenemos un servidor de Discord para hablar sobre el desarrollo, reportar errores o enterarte de las novedades de la app. 
* Puedes intentar unirte usando el **enlace de invitación que se encuentra dentro de la propia aplicación**.
* Si el enlace de la app ha caducado, **mándame un correo** y te enviaré una invitación nueva directamente. ¡Te esperamos!

---

> ## ⛔ ADVERTENCIA DE PROPIEDAD E INFRACCIÓN
> 
> **NO INTENTES SUBIR ESTA APLICACIÓN A OTRAS PLATAFORMAS O REPOSITORIOS EXTERNOS.**
> 
> MusicFlame es un proyecto de código abierto alojado **únicamente en este repositorio oficial**. Está **estrictamente prohibido** republicar, resubir o distribuir archivos APK de esta aplicación en tiendas de apps alternativas, foros o webs de terceros sin autorización previa. 
> 
> *   Si deseas compartirla o alojarla en otro lado, **debes contactarme obligatoriamente antes** a través de mi [correo](mailto:oomo87284@gmail.com) o por Discord para pedir permiso explícito.
> *   **Cualquier intento de rehosting no autorizado** será detectado de inmediato y se procederá a emitir una **denuncia de copyright (DMCA)** o aviso legal para el baneo y retiro forzoso de la publicación.
