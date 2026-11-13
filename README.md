# Mi TV

Versión 0.5.0 para celular Android y TV Box (Android 6 o superior). Catálogo inicial de 11 canales para probar en un TX1 con Android 10. Canal 9 fue confirmado por el usuario en su celular con la versión 0.1.0; los demás canales y el TV Box están pendientes de prueba física.

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

La versión 0.4.0 utiliza Room y un catálogo remoto versionado en `remote/catalog.json`. El asset se utiliza solo para la primera siembra. Más abajo se detalla cómo importar, editar, exportar y sincronizar sin perder las modificaciones locales.

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

La app usa internet y los permisos estándar de WorkManager para los trabajos programados. Favoritos, resultados y catálogo se guardan localmente en Room. Las páginas oficiales, sus reproductores y el navegador pueden usar cookies y servicios de terceros conforme a sus políticas; se permiten cookies de terceros en WebView para sus proveedores de video. No se agrega un servicio propio de analítica.

## Validación

`python3 tools/validate_catalog.py` verifica claves únicas, categorías, URLs del catálogo y la política Java de navegación, incluidos dominios falsos y esquemas no permitidos. GitHub Actions ejecuta esa validación, pruebas de rechazo de catálogos incompletos o maliciosos, Android Lint y compilación. Cuando hay un secreto de firma válido también verifica la firma y publica el APK junto a sus metadatos de actualización. Esto no sustituye las pruebas de imagen, sonido y control remoto en los equipos.

## Catálogo local con Room (0.4.0)

Room es la fuente de datos de ambas pantallas: canales y películas. La UI observa una consulta de la base que une entradas, grupos y resolvers. Favoritos, resultados manuales y salud de URLs también se guardan en Room. La base `mitv-catalog.db` tiene estas tablas (más `diagnostic_events` desde 0.5.0):

| Tabla | Contenido |
| --- | --- |
| `channels` | Canales, grupo, resolver, origen remoto, edición local, favorito y resultado |
| `movies` | Películas con las mismas relaciones y estado local |
| `groups` | Grupos seleccionables y creados desde el editor |
| `resolvers` | URL, página de origen, modo de reproducción y última salud de la URL |
| `metadata` | Siembra completada, fuente remota y versión aplicada por fuente |

`assets/channels.json` se lee exclusivamente dentro de la transacción de primera inicialización. Si la base está inicializada, no se abre ese asset otra vez, aunque todos los canales hayan sido eliminados. Un error de siembra revierte la transacción y permite reintentar. En la migración inicial también se recupera el antiguo `catalog_json` válido y los favoritos/resultados de SharedPreferences; después la UI no consulta esas preferencias. No hay recreación destructiva de la base. El esquema de Room se exporta en `app/schemas`.

Las películas empiezan vacías: no se añaden títulos ni fuentes de video sin que el usuario los importe o cree.

## Administrar, importar y exportar

Desde **Administrar / importar / exportar**:

- Crea, edita y elimina canales o películas. Elige un grupo existente o crea uno. Los modos son **Página web**, **Reproductor web** y **Video directo (HLS / MP4)**. Este último utiliza Media3; una página HTML no funciona como URL de video directo.
- Importa una lista por URL HTTP/HTTPS, archivo mediante el selector Android o texto pegado. Se aceptan M3U extendido (`#EXTM3U`), el JSON antiguo de canales y el formato JSON completo descrito debajo. Límite: 1 MB y 2000 entradas por importación.
- Antes de confirmar, se muestran canales/películas válidos, grupos, entradas nuevas, existentes, duplicados y errores. La validación y el resumen no escriben en la base. Al confirmar se guarda todo lo válido en una transacción; lo no incluido no se elimina. Las coincidencias se actualizan como ediciones locales. Cancelar no aplica cambios.
- En M3U el tipo por defecto es la sección desde la que se importa. Se leen `tvg-id`, `group-title`, `#EXTGRP` y las extensiones `x-mitv-kind`, `x-mitv-mode`, `x-mitv-site`. Sin modo explícito se utiliza video directo. Cabeceras personalizadas, DRM y directivas de Kodi/VLC no se ejecutan.
- Exporta el catálogo visible completo (canales y películas, excluidas eliminaciones) a JSON o M3U. Puedes **Guardar archivo** mediante Android o **Compartir con otra app** usando el selector del sistema. FileProvider comparte exclusivamente la carpeta temporal `exports`, con acceso de lectura al archivo elegido. No se solicita acceso general al almacenamiento.

El JSON mantiene grupos, resolvers, IDs y modos. M3U conserva los modos y tipo con atributos `x-mitv-*`; otros reproductores que no conocen esas extensiones solo podrán reproducir entradas que realmente apunten a un stream. Los enlaces de páginas web no se convierten en señales de video por exportarlos. Las exportaciones contienen las URLs que se hayan guardado, incluidas posibles claves de acceso del proveedor.

## Formato JSON completo

```json
{
  "schemaVersion": 1,
  "version": 1,
  "groups": [{"id": "regionales", "name": "Regionales"}],
  "resolvers": [{
    "id": "c9-player",
    "mode": "OFFICIAL_PLAYER",
    "url": "https://rudo.video/live/c9",
    "siteUrl": "https://www.canal9.cl/"
  }],
  "channels": [{
    "id": "c9",
    "name": "Canal 9 Bío Bío",
    "description": "Noticias y programas del Biobío",
    "groupId": "regionales",
    "resolverId": "c9-player"
  }],
  "movies": []
}
```

Se admiten también campos directos en cada entrada (`group`, `mode`, `url`, `siteUrl`) o los campos del JSON antiguo (`category`, `playerUrl`, `directPlayer`). `movies` usa el mismo formato que `channels`. IDs estables permiten actualizar una entrada existente. Las URLs aceptadas son HTTP/HTTPS sin usuario ni contraseña incrustados; no se ejecutan esquemas `file:`, `javascript:` o `intent:`.

## Sincronización versionada y protección local

La fuente por defecto es `remote/catalog.json`, separada del asset inicial. Se puede configurar otra URL HTTPS desde Administrar. Debe ofrecer JSON completo con `version` entero positivo creciente. Mi TV sincroniza al abrir y también bajo petición. El snapshot se valida antes de la transacción; una misma versión no se aplica otra vez y una inferior se rechaza. No se aplica un snapshot con errores o IDs duplicados.

La versión y el último catálogo aplicado quedan en Room, por fuente. Un fallo de red deja esa copia intacta. La sincronización actualiza únicamente entradas de su propia fuente que no fueron editadas localmente; favoritos y resultados se conservan. Las ediciones y eliminaciones locales quedan protegidas. Una eliminación crea una marca local para evitar que el servidor resucite la entrada. Las entradas remotas ausentes en una versión nueva dejan de mostrarse si no tienen cambios locales; las entradas de otras fuentes y las creadas/importadas localmente permanecen.

Para publicar cambios remotos, modifica `remote/catalog.json`, incrementa `version` y conserva los IDs. Esto no exige otro APK. `assets/channels.json` solo cambia la siembra de instalaciones nuevas.

## Salud de URLs con WorkManager

Se registra un trabajo periódico único cada 12 horas, con conexión y batería suficiente. Android puede retrasarlo por ahorro de energía. También se puede encolar una comprobación manual desde Administrar.

Cada ejecución comprueba hasta 50 resolvers, priorizando los menos recientes, con un máximo de cuatro peticiones simultáneas y un tiempo total acotado. Utiliza HEAD; si el servidor devuelve 405/501, intenta GET con `Range: bytes=0-0`, sin descargar el video. Las respuestas 2xx indican URL accesible, 404/410 marcan caída y otros fallos transitorios requieren tres comprobaciones fallidas consecutivas. 401/403/429 se dejan sin confirmar para no confundir autenticación, protección o límite de peticiones con una señal caída. Editar la URL o el modo reinicia su estado de salud. La comprobación guarda el resultado solo si la URL no fue cambiada durante la petición.

Una página accesible no prueba que el video funcione. El marcado no elimina entradas ni bloquea su reproducción. Catálogos de más de 50 resolvers se revisan por lotes en ejecuciones sucesivas.

## Pruebas de la migración

Se incluyen pruebas de Room con SQLite bajo Robolectric para siembra única, reapertura, migración de preferencias, cancelación de importación, protección de ediciones/eliminaciones, versiones remotas y CRUD de películas. También se prueban la importación/exportación M3U/JSON, validación y las comprobaciones HEAD/GET con un servidor local de pruebas. GitHub ejecuta estas pruebas y Android Lint antes de publicar un APK.

## Diagnóstico y ajustes (0.5.0)

**Diagnóstico** muestra los últimos 200 eventos locales, del más reciente al más antiguo, con fecha, nivel y categoría. **Copiar diagnóstico** copia esa vista al portapapeles únicamente al pulsarlo. Se registran inicio, preparación de la base, fallos de operaciones, sincronización, importaciones/exportaciones, carga de reproductores, comprobaciones de salud, actualizaciones y temporizador. Los paths, parámetros y credenciales de URLs se ocultan; se conserva el host para identificar el servicio. También se ocultan posibles cabeceras de autenticación y valores de PIN/tokens. No se envía el diagnóstico a un servidor.

Room pasa de esquema 1 a 2 mediante `MIGRATION_1_2`: solo añade `diagnostic_events`, conservando canales, películas, grupos, resolvers, favoritos, resultados y metadatos. La inserción de eventos y el recorte a 200 son transaccionales y se ejecutan fuera del hilo de la UI.

En **Ajustes** se pueden guardar:

| Ajuste | Comportamiento |
| --- | --- |
| User-Agent | Vacío mantiene el predeterminado de WebView; en peticiones directas usa MiTV. Se aplica al reproductor, descargas de catálogo y comprobaciones de URLs. |
| Referer por dominio | Formato `dominio=https://página/`, una línea por dominio. Abarca subdominios y prima la coincidencia más específica. Se recalcula por URL solicitada, incluyendo segmentos del reproductor nativo y comprobaciones de salud. En WebView se añade a la navegación principal; los subrecursos siguen las reglas del navegador/proveedor. |
| Calidad | Automática o altura máxima 480p/720p/1080p en video directo. Solo se pueden seleccionar calidades que la fuente realmente ofrezca. Los reproductores web gestionan su propia calidad. |
| Buffer | Bajo (2–10 s), normal (15–30 s) o alto (30–60 s), con umbrales de inicio y rebuffer compatibles. Se aplica al abrir el siguiente video directo. No controla el buffer interno de páginas web. |
| PIN | Opcional, de 4 a 8 dígitos; protege abrir administración y ajustes. No bloquea canales ni películas. Se guarda únicamente un hash PBKDF2 con sal aleatoria. Cinco intentos fallidos producen un bloqueo temporal de un minuto. Para cambiarlo o quitarlo hay que acceder primero con el PIN actual. |
| Temporizador | Desactivar o iniciar 15/30/60/90/120 minutos al guardar. Al expirar detiene el reproductor de Mi TV y vuelve a canales/películas. El plazo se conserva si se sale y vuelve a la app; una reproducción pausada no se reanuda si el tiempo ya expiró. No controla otros navegadores o apps. |

Los ajustes se guardan localmente, fuera de los catálogos importables/exportables. Guardar otros ajustes no reinicia un temporizador activo a menos que se marque **Cambiar temporizador al guardar**.

La comprobación de versión usa el JSON remoto de la última Release del repositorio. Hay un botón manual y una comprobación silenciosa al abrir, como máximo una vez al día después de un resultado válido. Una versión compatible superior muestra el aviso con enlace de descarga; el aviso automático se presenta una vez por versión. Se siguen validando versión, paquete, certificado y origen del APK; Android confirma la instalación. Un fallo automático queda en diagnóstico sin interrumpir al usuario.

La tabla `movies` ya utiliza el mismo patrón `groupId`/`resolverId` de canales. **Gestionar películas** abre directamente su pantalla de CRUD, manteniendo importación, exportación, reproducción, estado de salud y protección frente a sincronización remota.

## Lista compacta (0.5.1)

Canales y películas se muestran en filas con el nombre y dos iconos: reproducir y favorito. Las acciones conservan etiquetas de accesibilidad, áreas de 52 dp y foco visible para el control remoto. Descripciones, categorías repetidas y resultados no ocupan cada fila; filtros y administración se mantienen. El resultado manual sigue disponible en el reproductor.
