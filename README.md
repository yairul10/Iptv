# Mi TV

Primera prueba personal para celular Android y TV Box (Android 6 o superior), diseñada para comprobar Canal 9 Bío Bío en un TX1 con Android 10.

## Descargar e instalar

1. Abre la pestaña [Actions](https://github.com/yairul10/Iptv/actions).
2. Abre la última ejecución verde de **Generar APK de Mi TV**.
3. En **Artifacts**, descarga **Mi-TV-APK**. Necesitas iniciar sesión en GitHub.
4. Extrae el ZIP e instala **Mi-TV.apk**. Autoriza esa fuente si Android lo solicita.
5. En el TV Box puedes transferir el APK mediante un pendrive.

Se compila automáticamente al actualizar `main`. También puedes pulsar **Run workflow** desde Actions. Si GitHub solicita habilitar Actions, acepta para este repositorio.

## Qué incluye

- Canal 9 usando `https://rudo.video/live/c9`, el reproductor que enlaza el [sitio oficial](https://www.canal9.cl/).
- Favorito guardado en el dispositivo, lista de favoritos, recarga y acceso al sitio oficial.
- Botones con foco visible, navegación con flechas/OK y soporte táctil.
- Vista amplia y pantalla completa de videos HTML, cuando el reproductor la ofrece.
- Sin cuentas, servidor propio, extracción de enlaces de video ni retransmisión.

**Esto es una prueba de acceso al reproductor web oficial, no un reproductor IPTV nativo.** No se ha verificado todavía la reproducción en el TX1 ni en un teléfono físico. No se afirma tener autorización para redistribuir la señal. No se eliminan anuncios ni restricciones del proveedor. El enlace oficial podría cambiar o restringir WebView.

En el reproductor pulsa Play si lo pide. El botón Play/pausa controla video HTML en la página principal; si el proveedor cambia a un iframe puede requerir usar sus propios controles. MENU del control alterna la vista amplia; Atrás restaura los controles y luego vuelve a los canales. Algunas versiones antiguas de WebView pueden requerir actualización desde la tienda del equipo. La navegación dentro del reproductor depende del proveedor.

## Firma y actualizaciones

La compilación inicial genera una **versión de prueba** (`cl.mitv.app.preview`). Su clave se conserva en la caché de Actions y nunca se publica en el repositorio ni en los archivos descargables. **La caché puede eliminarse**, y en ese caso la siguiente APK requerirá desinstalar la anterior, perdiendo sus favoritos.

Para una firma duradera, crea y conserva un keystore privado fuera del repositorio y agrega estos **repository secrets** en Settings → Secrets and variables → Actions:

- `KEYSTORE_BASE64`: contenido del keystore convertido a Base64.
- `KEYSTORE_PASSWORD`: contraseña del keystore.
- `KEY_ALIAS`: alias de la clave.
- `KEY_PASSWORD`: contraseña de la clave.

Con esos cuatro secretos se genera una versión personal de release (`cl.mitv.app`). Instálala una vez junto a la prueba; las actualizaciones release conservarán favoritos mientras se use esa misma clave y un `versionCode` mayor. No subas el keystore ni sus contraseñas al repositorio público. Haz una copia privada del keystore.

## Desarrollo

Java 17, Android SDK 35, Android Gradle Plugin 8.9.2, Gradle 8.11.1. Abre el proyecto en Android Studio o instala esa versión de Gradle y ejecuta `gradle lintDebug assembleDebug`. No se incluye Gradle Wrapper; Actions instala una versión fijada de Gradle.

La app solo solicita acceso a internet y conserva el favorito localmente. El reproductor oficial y el navegador pueden usar cookies y servicios de terceros conforme a sus propias políticas.
