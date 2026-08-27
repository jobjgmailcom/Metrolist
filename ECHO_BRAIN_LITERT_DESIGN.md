# MetroList Echo Brain Neural FOSS

Esta rama parte de MetroList oficial `f806b557` y porta únicamente la capa Echo Brain local: inyección tras la pista activa, protección de cola original, umbrales 90/80/70/60, bloqueo de duplicados y variantes, cooldown de 24 horas, diversidad, continuidad, confirmación de escucha, secuencias y perfil Flow-compatible.

La APK usa un `applicationId` independiente, `com.metrolist.music.echobrain.neuro`, por lo que se instala junto a cualquier APK anterior de MetroList o MetroList Echo Brain. La variante FOSS no usa cuentas, claves, telemetría, modelos remotos ni un resolvedor de streams adicional.

## LiteRT local

Se incluye un modelo TensorFlow Lite/LiteRT CPU muy pequeño y estático, generado por `scripts/create_echo_brain_ranker_model.py`. Su trabajo exclusivo es desempatar una pequeña lista de candidatas que ya pasaron todos los filtros estrictos de Echo Brain. Usa posición dentro de la radio nativa, coincidencia de artista/álbum/título y el perfil local de escucha.

El modelo no analiza audio, no inventa género, no consulta red y no puede aceptar una canción bloqueada. Si LiteRT no está disponible, Echo Brain conserva el orden clásico de MetroList. LiteRT 1.4.2 se distribuye bajo Apache-2.0; el código añadido de la aplicación sigue GPLv3 y la atribución Flow se conserva en `FLOW_NEURO_ATTRIBUTION.md`.
