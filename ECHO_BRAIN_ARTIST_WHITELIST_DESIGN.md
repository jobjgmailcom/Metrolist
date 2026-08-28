# Lista blanca local de artistas para Echo Brain

## Propósito

La lista blanca limita **sólo las candidatas que Echo Brain puede insertar**. No elimina, reordena ni oculta elementos de la cola original; si no existe una candidata permitida que cumpla las reglas de afinidad, el ciclo no inserta nada.

## Datos y límite

| Dato | Persistencia | Regla |
|---|---|---|
| Activar lista blanca | `booleanPreferencesKey` | Desactivada por defecto para no cambiar instalaciones existentes. |
| Artistas permitidos | `stringPreferencesKey` | Texto normalizado, máximo 20.000 entradas. |
| Artistas bloqueados | No se añade en esta versión | El usuario puede quitar un nombre; el filtro sigue siendo exclusivamente de permitidos. |

El editor admite nombres separados por salto de línea, coma o punto y coma. Antes de guardar, el texto se divide, se recortan espacios, se pliegan mayúsculas y acentos para deduplicar, se mantiene el primer nombre legible y se conservan las primeras 20.000 entradas válidas. No se manda ni sincroniza esta información.

## Regla de selección

El servicio mantiene la lista normalizada en memoria junto a los demás ajustes de Echo Brain. Tanto las relaciones locales como la radio de respaldo pasan la misma regla de planificador:

1. Se aplican primero identidad canónica, cola existente, cooldown, versiones, diversidad y similitud.
2. Cuando la lista blanca está activada, el artista principal de la candidata debe estar en los artistas permitidos.
3. Sólo después se reordena el pequeño conjunto apto con LiteRT local y se inserta como máximo el lote ya configurado.

La etiqueta visible de la cola conserva `Echo Brain · …`; el diagnóstico local indicará cuando una lista blanca activa no produjo candidata. El filtro no hace peticiones de red, no modifica reproducción y no puede convertir una canción no permitida en candidata válida.
