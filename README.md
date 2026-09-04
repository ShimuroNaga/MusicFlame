# MusicFlame 🔥🎵

![Estado del Proyecto](https://img.shields.io/badge/Estado-En_Desarrollo_Activo-brightgreen)
![Plataforma](https://img.shields.io/badge/Plataforma-Android-blue)
![Versión](https://img.shields.io/badge/Versión-3.12-orange)

**MusicFlame** es un reproductor de música versátil y moderno que combina tu biblioteca local con el extenso catálogo de YouTube en una sola aplicación. Diseñado con un enfoque en la personalización, la estética y el rendimiento.

---

## ✨ Características Principales

### 🎵 Gestión de Música y Reproducción
* **Buscador Dual:** canciones locales o en línea vía YouTube.
* **Tu Mix Diario:** mix aleatorio diario, listo para reproducir o guardar como playlist.
* **Filtros Inteligentes:** ordena por fecha, A-Z o Z-A; filtra por duración y por **formato de audio** (nuevo panel que detecta los formatos presentes en tu librería y permite ocultar los que no quieras).
* **Selección Múltiple:** modo selección con "Seleccionar todos".
* **Control Único de Reproducción:** un botón cíclico Normal → Aleatorio → Repetir Todo → Repetir Una.
* **Letras Sincronizadas:** búsqueda automática (LRC), animación configurable (Deslizar/Desvanecer/Rebote), verificación manual vía YouTube o edición a mano, y ahora con **color personalizable**.
* **Visualizador de Audio Real:** 32 barras de espectro reales, ahora con **6 estilos adicionales**: espejado, ondas de agua, círculo pulsante, partículas, barras finas y VU meter retro — además del color del ecualizador personalizable.
* **Audio Avanzado:** ecualizador de 5 bandas, Bass Boost, Virtualizer, Loudness Enhancer, Reverb y optimización de batería.

### 💿 Álbumes, Artistas y Géneros
* Explora tu biblioteca por álbum, y ahora también **por artista** y **por género** (con detalle dedicado para cada uno).
* Exporta un álbum completo como playlist M3U, o reordénalo (original / A-Z / Z-A).

### 🗂️ Playlists, Papelera y Mantenimiento
* Crea playlists desde cualquier pantalla.
* **Papelera de Reciclaje** para canciones y playlists, con borrado automático a los 30 días.
* **Búsqueda de anomalías** (bajo demanda): detecta canciones con carátula corrupta, metadata faltante o formato no soportado.
* **Edición real de metadatos:** los cambios de carátula, álbum, artista y título ahora se pueden guardar directamente en el archivo de audio (mp3, flac, ogg, wav, m4a, wma, aiff, dsf, opus), con respaldo automático antes de escribir.

### ☁️ Respaldo en Google Drive
* Sube canciones a una carpeta dedicada dentro de tu Drive como respaldo fuera del dispositivo.

### 🎨 Personalización Profunda
* **Temas Visuales:** Sistema/Material You, Claro, Oscuro y Modo AMOLED.
* **Tipografía:** selector de **tipo de letra** para toda la app y selector de **tamaño de letra**, ambos con vista previa en vivo.
* **Fondos Personalizados:** imágenes o GIFs con brillo ajustable.
* **Color de Texto y del Ecualizador:** blanco, negro o modos avanzados.
* **Forma de Carátula:** cuadrado, círculo, hexágono, vinilo o squircle.
* **Iconos Alternativos** de la app.
* **Interfaz:** bordes redondeados, columnas de la cuadrícula, opacidad del widget.

### 🎧 Widgets de Pantalla de Inicio
* Widget clásico con carátula, Play/Pausa, Siguiente y Anterior.
* **Nuevo widget de vinilo**, con la carátula girando en un recorte de medio círculo.
* Carátulas del widget corregidas para mostrarse siempre correctamente (antes fallaban en algunos casos).

### 🔄 Actualizaciones
* Aviso dentro de la app cuando hay una versión nueva, con opción de ignorarla.

### 🚀 Primer Uso Guiado
* Onboarding paso a paso: permisos, importación, apariencia, cuenta y letras.

---

## 🆕 Novedades de la versión 3.12

* Selector global de **tipo y tamaño de letra** para toda la app.
* **6 estilos nuevos de ecualizador gráfico** + color personalizable.
* Nuevo **widget de vinilo** con carátula giratoria.
* Biblioteca navegable **por artista** y **por género**.
* **Guardado real de metadatos** en el archivo de audio (mp3, flac, ogg, wav, m4a, wma, aiff, dsf, opus).
* Nuevo panel de **formatos de audio** detectados en tu librería.
* Nueva herramienta de **búsqueda de anomalías** (carátulas corruptas, metadata faltante, formatos no soportados).
* **Correcciones de carátula:** ya no fallan en la notificación de reproducción ni se repiten entre canciones del mismo álbum; mejor ícono de carga.
* **Mejoras de rendimiento:** solucionado el lag del modo Arcoíris y optimizada la pantalla de reproducción a pantalla completa.
* Sistema de compras dentro de la app para desbloquear extras cosméticos.

---

## 🤖 ¿Y la IA (Gemini)?

Versiones anteriores incluían integración con Gemini para chatear y pedir recomendaciones musicales. **Esa función ya no está disponible**: sostenerla requería un backend de pago en Firebase, algo insostenible para un proyecto personal y gratuito.

Ese espacio se aprovechó para construir Álbumes, Artistas, Géneros y el resto de funciones nativas que no dependen de ningún servidor de pago y funcionan 100% offline salvo cuando específicamente necesitan internet (YouTube, letras o Drive).

---

## 🚀 Instalación

1. Ve a la sección de [Releases](../../releases) de este repositorio.
2. Busca la versión más reciente.
3. En **Assets**, descarga el archivo `.apk` (ej. `MusicFlame-v3.12.apk`).
4. Ábrelo en tu dispositivo Android *(puede pedir permiso para instalar de orígenes desconocidos)*.
5. ¡Listo! Abre la app, concede los permisos de audio y disfruta.

---

## 🛠️ Aviso de Desarrollo

> MusicFlame es un proyecto en desarrollo activo. La 3.12 trae personalización tipográfica, más estilos de ecualizador, navegación por artista/género, edición real de metadatos y varias correcciones de estabilidad y rendimiento.
>
> Si notas algo extraño o un *bug*, por favor repórtalo. ¡Gracias por la paciencia!

---

## 👥 Colaboradores y Testers

Gracias a estas personas por su ayuda en *testing* y reporte de errores:

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

* **Desarrollador Principal:** ShimuroNaga
* **Correo de contacto / Reporte de bugs:** oomo87284@gmail.com

### 👾 Servidor de Discord
* Únete con el enlace de invitación dentro de la propia aplicación.
* Si el enlace ha caducado, escríbeme por correo y te envío uno nuevo.

---

> ## ⛔ ADVERTENCIA DE PROPIEDAD E INFRACCIÓN
>
> **NO INTENTES SUBIR ESTA APLICACIÓN A OTRAS PLATAFORMAS O REPOSITORIOS EXTERNOS.**
>
> MusicFlame es un proyecto de código abierto alojado **únicamente en este repositorio oficial**. Está **estrictamente prohibido** republicar, resubir o distribuir archivos APK en tiendas de apps alternativas, foros o webs de terceros sin autorización previa.
>
> * Si deseas compartirla o alojarla en otro lado, **contáctame obligatoriamente antes** por [correo](mailto:oomo87284@gmail.com) o Discord para pedir permiso explícito.
> * **Cualquier intento de rehosting no autorizado** será detectado y se procederá con una denuncia de copyright (DMCA) o aviso legal para el baneo y retiro forzoso de la publicación.
