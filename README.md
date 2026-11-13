# Mi TV

Versión 0.2.0 para celular Android y TV Box (Android 6 o superior). Catálogo inicial de 11 canales para probar en un TX1 con Android 10. Canal 9 fue confirmado por el usuario en su celular con la versión 0.1.0; los demás canales y el TV Box están pendientes de prueba física.

## Descargar e instalar

1. Abre la pestaña [Actions](https://github.com/yairul10/Iptv/actions).
2. Abre la última ejecución verde de **Generar APK de Mi TV**.
3. En **Artifacts**, descarga **Mi-TV-APK**. Necesitas iniciar sesión en GitHub.
4. Extrae el ZIP e instala **Mi-TV.apk**. Autoriza esa fuente si Android lo solicita.
5. En el TV Box puedes transferir el APK mediante un pendrive.

Se compila automáticamente al actualizar `main`. También puedes pulsar **Run workflow** desde Actions. Si GitHub solicita habilitar Actions, acepta para este repositorio.

## Canales incluidos

| Canal | Acceso dentro de Mi TV | Fuente oficial |
| --- | --- | --- |
| Canal 9 Bío Bío | Reproductor web enlazado por el canal | https://www.canal9.cl/ |
| TVU | Reproductor web enlazado por el canal | https://www.tvu.cl/ |
| TV+ | Reproductor web enlazado por el canal | https://tvmas.tv/page/en-vivo |
| TVN | Página de señal oficial | https://www.tvn.cl/en-vivo |
| Chilevisión | Página de señal oficial | https://www.chilevision.cl/senal-online/ |
| Mega | Página de señal oficial | https://www.mega.cl/senal-en-vivo/ |
| Canal 13 | Página de señal oficial | https://www.13.cl/en-vivo |
| 24 Play | Página de señal digital oficial (no se presenta como Canal 24 Horas de pago) | https://www.tvn.cl/senal-24play |
| T13 En Vivo | Página de señal oficial | https://www.t13.cl/en-vivo |
| Meganoticias Ahora | Página de señal oficial | https://www.mega.cl/senal-en-vivo/meganoticias/ |
| NTV | Página de señal oficial | https://www.tvn.cl/senal-ntv |

Las fuentes se revisaron el 12 de noviembre de 2026 (Chile). Una página accesible no confirma reproducción, disponibilidad de todos los programas ni compatibilidad con TV Box. Puede pedir cookies, Play, activación de sonido o aplicar restricciones del proveedor. No se saltan pagos, bloqueos ni anuncios.

## Uso y pruebas

- Categorías: todos, regionales, nacionales, noticias e infantil/cultura; combinables con favoritos.
- Favoritos independientes por canal; el favorito de Canal 9 de la versión anterior se conserva al actualizar con la misma firma.
- **Resultado** permite marcar cada canal como Pendiente, Funciona o No funciona. Se guarda solo en ese dispositivo: los resultados del celular no se confunden con los del TV Box.
- Flechas y OK para navegar; en páginas completas se conservan sus propios controles. En los reproductores de Canal 9, TVU y TV+, OK intenta alternar Play/pausa cuando el foco está en el video.
- **Ampliar vista** oculta los botones de Mi TV; Atrás los recupera. Usa el botón de pantalla completa del propio reproductor para ampliar solo el video. MENU también alterna la vista.
- **Recargar** vuelve a la señal inicial del canal; **Página oficial** abre el navegador externo como alternativa.
- Al salir a otra aplicación se detiene el reproductor y se vuelve a cargar al regresar, para evitar audio oculto de reproductores insertados.
- Si una página falla, comprueba internet, actualiza Android System WebView si tu equipo lo permite y prueba en el navegador.

La aplicación usa páginas y reproductores web oficiales, no un reproductor IPTV nativo. No está afiliada a los canales, no extrae enlaces de transmisión, no retransmite señales y no afirma autorización de redistribución. La navegación de cada página con el control remoto depende de su diseño. No se ha verificado físicamente la nueva versión.

El catálogo editable está en `app/src/main/assets/channels.json`. Sus cambios se incluyen en cada APK; esta versión no descarga automáticamente un catálogo remoto. Cada canal tiene una clave estable para conservar su favorito y su resultado.

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

La app solo solicita acceso a internet. Favoritos y resultados se guardan localmente. Las páginas oficiales, sus reproductores y el navegador pueden usar cookies y servicios de terceros conforme a sus políticas; se permiten cookies de terceros en WebView para sus proveedores de video. No se agrega un servicio propio de analítica.

## Validación

`python3 tools/validate_catalog.py` verifica claves únicas, categorías, URLs del catálogo y la política Java de navegación, incluidos dominios falsos y esquemas no permitidos. GitHub Actions ejecuta esa validación, Android Lint, compilación y verificación de firma del APK. Esto no sustituye las pruebas de imagen, sonido y control remoto en los equipos.
