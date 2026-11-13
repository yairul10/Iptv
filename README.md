# Mi TV

Versión 0.3.0 para celular Android y TV Box (Android 6 o superior). Catálogo inicial de 11 canales para probar en un TX1 con Android 10. Canal 9 fue confirmado por el usuario en su celular con la versión 0.1.0; los demás canales y el TV Box están pendientes de prueba física.

## Descargar e instalar

La primera versión con firma fija se genera después de configurar el secreto privado indicado más abajo.

1. Abre [Releases](https://github.com/yairul10/Iptv/releases/latest).
2. Descarga **Mi-TV.apk** e instálalo. Autoriza esa fuente si Android lo solicita.
3. Para el TV Box puedes pasar ese APK en un pendrive.

**Migración desde 0.1/0.2:** la ruta de la caché de firma anterior era incorrecta y la clave no quedó guardada. Será necesario desinstalar esa versión de prueba una última vez (se pierden sus favoritos y resultados), e instalar 0.3.0. Desde entonces, la misma firma y el mismo paquete permiten actualizar conservando los datos.

En Mi TV 0.3.0, pulsa **Buscar actualización de la app**. La app lee `update.json` de la última Release, compara versión, paquete y huella de firma, y solo ofrece la descarga si son compatibles. Descarga el APK en el navegador y ábrelo para confirmar **Actualizar** en Android. No instala silenciosamente ni pide permisos para instalar otras aplicaciones.

Cada cambio de código en `main` ejecuta pruebas, Android Lint y compilación. Solo se publica una Release instalable cuando existe la firma fija válida. También puedes ejecutar **Run workflow** en [Actions](https://github.com/yairul10/Iptv/actions/workflows/android.yml).

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

## Actualizar canales sin reinstalar

El catálogo editable está en `app/src/main/assets/channels.json`. Mi TV consulta su versión pública en GitHub al abrir la app; también se puede pulsar **Actualizar canales**. GitHub puede tardar unos minutos en servir un cambio reciente.

Cada catálogo se valida completo antes de reemplazar la copia local. Si no hay internet o el JSON es inválido, se conserva la última lista válida; al instalar por primera vez se usa la lista incluida en el APK. Las claves de canal son estables para conservar favoritos y resultados. Las categorías y los dominios iniciales de cada canal se leen del catálogo, por lo que añadir otra categoría o un canal con otro dominio HTTPS no exige un APK nuevo. Los cambios no cierran una señal que se está reproduciendo.

Los cambios que afecten únicamente al catálogo ejecutan su validación en Actions y no generan otro APK. No se configura un servidor de pago. El catálogo y los enlaces publicados son públicos: no incluyas credenciales ni enlaces privados.

## Firma fija y respaldo privado

Paquete permanente: `cl.mitv.app.preview`. La huella pública de firma está en `config/signing-sha256.txt`. Nunca se genera una clave nueva durante la compilación ni se almacena en una caché. Si falta el secreto o no coincide la firma, no se publica un APK instalable.

El propietario conserva un respaldo privado de la firma, separado del repositorio. Para habilitar las compilaciones firmadas:

1. Abre [New repository secret](https://github.com/yairul10/Iptv/settings/secrets/actions/new).
2. Name: `MITV_SIGNING_BUNDLE`.
3. Secret: contenido completo del archivo privado `MITV_SIGNING_BUNDLE.txt` entregado al propietario. Contiene el keystore en Base64, su contraseña y el alias.
4. Guarda y ejecuta **Run workflow** en Actions.

Guarda una copia privada del respaldo. No publiques ese ZIP, el archivo de secreto ni el keystore. Solo la huella del certificado debe estar en este repositorio público. Si el secreto se borra, restáuralo desde el mismo respaldo; generar otra firma impedirá actualizar las instalaciones existentes.

Android debe confirmar cada instalación. Mi TV ofrece el APK desde la Release oficial de este repositorio; Android también valida su firma al actualizar.

## Desarrollo

Java 17, Android SDK 35, Android Gradle Plugin 8.9.2, Gradle 8.11.1. Abre el proyecto en Android Studio o instala esa versión de Gradle y ejecuta `gradle testDebugUnitTest lintDebug assembleDebug`. No se incluye Gradle Wrapper; Actions instala una versión fijada de Gradle.

La app solo solicita acceso a internet. Favoritos y resultados se guardan localmente. Las páginas oficiales, sus reproductores y el navegador pueden usar cookies y servicios de terceros conforme a sus políticas; se permiten cookies de terceros en WebView para sus proveedores de video. No se agrega un servicio propio de analítica.

## Validación

`python3 tools/validate_catalog.py` verifica claves únicas, categorías, URLs del catálogo y la política Java de navegación, incluidos dominios falsos y esquemas no permitidos. GitHub Actions ejecuta esa validación, pruebas de rechazo de catálogos incompletos o maliciosos, Android Lint y compilación. Cuando hay un secreto de firma válido también verifica la firma y publica el APK junto a sus metadatos de actualización. Esto no sustituye las pruebas de imagen, sonido y control remoto en los equipos.
