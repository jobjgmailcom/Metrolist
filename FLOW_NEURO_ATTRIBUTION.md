# Adaptación FlowNeuro en MetroList Echo Brain

MetroList ya se distribuye bajo **GNU GPL v3.0**. La capa `EchoBrainNeuroProfile.kt` adapta una parte limitada de la arquitectura de FlowNeuro de [Flow Android Client](https://github.com/A-EDev/Flow), revisión `3fcc95c32b0f80bedb55e5bf2f6571f2c9bda483`.

| Fuente de Flow | Uso adaptado en MetroList | Modificación de MetroList |
| --- | --- | --- |
| `data/recommendation/NeuroModels.kt` | Contrato compacto de vector de contenido. | Sólo metadatos musicales disponibles localmente; no vídeo, Shorts, cuenta ni perfil remoto. |
| `data/recommendation/NeuroVectorMath.kt` | Similitud coseno y ajuste vectorial puros. | Renombrado y limitado a desempatar candidatas que ya aprobaron Echo Brain. |
| `data/recommendation/FlowNeuroEngine.kt` | Separación entre orquestación, estado local y cálculo puro. | La orquestación sigue en `MusicService`; usa los eventos existentes de confirmación y salto temprano. |

La adaptación no puede volver elegible a una canción bloqueada. Conserva la inserción de una canción por ciclo, cola original, umbrales 90/80/70/60, exclusión de duplicados y variantes, cooldown de 24 horas, diversidad de artistas, continuidad Dominante, secuencias locales y privacidad sin telemetría. El perfil se guarda exclusivamente en las preferencias locales de MetroList.

El código derivado preserva aviso GPLv3, crédito a Flow Android Client y enlace al repositorio original en su cabecera. Las distribuciones de MetroList que incluyan este archivo deben conservar esta atribución, la licencia GPLv3 y el código fuente correspondiente.
