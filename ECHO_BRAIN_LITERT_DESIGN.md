# MetroList Echo Brain Neural FOSS

Esta rama parte de MetroList oficial `f806b557` y porta únicamente la capa Echo Brain local: inyección tras la pista activa, protección de cola original, umbrales 90/80/70/60, bloqueo de duplicados y variantes, cooldown de 24 horas, diversidad, continuidad, confirmación de escucha, secuencias y perfil Flow-compatible.

La APK usa un `applicationId` independiente, `com.metrolist.music.echobrain.neuro`, por lo que se instala junto a cualquier APK anterior de MetroList o MetroList Echo Brain. La variante FOSS no usa cuentas, claves, telemetría, modelos remotos ni un resolvedor de streams adicional.

## LiteRT local

Se incluye un modelo TensorFlow Lite/LiteRT CPU muy pequeño y estático, generado por `scripts/create_echo_brain_ranker_model.py`. Su trabajo exclusivo es desempatar una pequeña lista de candidatas que ya pasaron todos los filtros estrictos de Echo Brain. Usa posición dentro de la radio nativa, coincidencia de artista/álbum/título y el perfil local de escucha.

El modelo no analiza audio, no inventa género, no consulta red y no puede aceptar una canción bloqueada. Si LiteRT no está disponible, Echo Brain conserva el orden clásico de MetroList. LiteRT 1.4.2 se distribuye bajo Apache-2.0; el código añadido de la aplicación sigue GPLv3 y la atribución Flow se conserva en `FLOW_NEURO_ATTRIBUTION.md`.

## Validación de la relación de radio

La radio nativa ya filtrada proporciona una señal acotada de relación: sus ocho primeras candidatas seguras reciben 96–90 puntos y las siguientes se mantienen en 88. Esta señal sólo se evalúa después de excluir la cola original, inyecciones previas, duplicados canónicos, artistas bloqueados y versiones no permitidas. Por ello, en similitud estricta de 90 % y diversidad alta, una candidata relacionada de otro artista puede seguir siendo elegible sin rebajar el umbral ni abrir una búsqueda general de catálogo.

Las pruebas unitarias locales verifican esa ruta, el límite fuera de las primeras ocho candidatas y todas las exclusiones duras. La prueba funcional pendiente es distinta: en un teléfono, la evidencia válida será una o hasta tres filas con `Echo Brain · …` justo después de la pista activa y el resultado correspondiente en **Ajustes → Echo Brain → Último resultado local**.
