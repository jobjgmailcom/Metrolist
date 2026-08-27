# MetroList Echo Brain Neural FOSS

Esta rama parte de MetroList oficial `f806b557` y porta únicamente la capa Echo Brain local: inyección tras la pista activa, protección de cola original, umbrales 90/80/70/60, bloqueo de duplicados y variantes, cooldown de 24 horas, diversidad, continuidad, confirmación de escucha, secuencias y perfil Flow-compatible.

La APK usa un `applicationId` independiente, `com.metrolist.music.echobrain.neuro`, por lo que se instala junto a cualquier APK anterior de MetroList o MetroList Echo Brain. La variante FOSS no usa cuentas, claves, telemetría, modelos remotos ni un resolvedor de streams adicional.

## LiteRT local

Se incluye un modelo TensorFlow Lite/LiteRT CPU muy pequeño y estático, generado por `scripts/create_echo_brain_ranker_model.py`. Su trabajo exclusivo es desempatar una pequeña lista de candidatas que ya pasaron todos los filtros estrictos de Echo Brain. Combina únicamente señales de metadatos y perfil local; no convierte la posición de una radio en prueba de afinidad.

El modelo no analiza audio, no inventa género, no consulta red y no puede aceptar una canción bloqueada. Si LiteRT no está disponible, Echo Brain conserva el orden clásico de MetroList. LiteRT 1.4.2 se distribuye bajo Apache-2.0; el código añadido de la aplicación sigue GPLv3 y la atribución Flow se conserva en `FLOW_NEURO_ATTRIBUTION.md`.

## Validación de la relación de radio

La radio nativa se usa como fuente de candidatas, no como demostración de que cada resultado es similar. Tras excluir cola original, inyecciones previas, duplicados canónicos, artistas bloqueados y versiones no permitidas, una candidata de radio sólo alcanza el umbral elegido con evidencia explícita: coincidencia de artista o señales locales de contexto ya guardadas. Su posición dentro de la página de radio aporta cero puntos.

Las pruebas unitarias verifican que una radio puede conservar una coincidencia explícita de ancla y que canciones ajenas como «Tarzan Boy» no pasan 90 % por aparecer al principio de una radio. La prueba funcional pendiente es distinta: en un teléfono, la evidencia válida será una o hasta tres filas con `Echo Brain · …` justo después de la pista activa y el resultado correspondiente en **Ajustes → Echo Brain → Último resultado local**.
