# Plan de implementacion - Version 1 monolitica

## 1. Proposito

Implementar la primera version del calculo de velocidades promedio por ruta por mes para todas las rutas activas del piloto SITM-MIO.

Esta version debe usar:

```text
sitm-mio/data/lines-241-ActiveGT.csv
sitm-mio/data/datagrams-MiniPilot.csv
```

La version monolitica sera el baseline de correctitud y rendimiento. Las versiones concurrente y distribuida deberan producir los mismos resultados que esta version.

## 2. Alcance de esta version

Incluido:

- Lectura de rutas activas.
- Lectura de datagramas del dataset MiniPilot.
- Validacion y limpieza basica de datos.
- Calculo secuencial de velocidad promedio por `lineId`, `year`, `month`.
- Generacion de reporte CSV.
- Registro de metricas de ejecucion.
- Validacion manual de una muestra.

No incluido en esta version:

- Procesamiento concurrente.
- Procesamiento distribuido.
- Almacenamiento en base de datos.
- Visualizacion grafica.
- Simulacion en tiempo real con `bus-simulator`.

## 3. Decision arquitectonica principal

### Decision

Implementar la version monolitica como un flujo batch dentro del modulo:

```text
sitm-mio/data-center
```

### Justificacion

`data-center` ya representa el componente de almacenamiento y analisis historico en la arquitectura actual. El requerimiento de velocidad promedio mensual es historico, no de tiempo real, por lo que debe vivir ahi.

No se usara `bus-simulator` para esta version porque el simulador introduce retrasos artificiales y representa eventos en tiempo real. Para calcular reportes historicos, conviene leer directamente el CSV y procesarlo como lote.

### Consecuencia

La version monolitica podra ejecutarse como comando batch. Si `data-center` se ejecuta sin argumentos, conservara su comportamiento actual como servidor Ice. Si se ejecuta con el modo `monolithic`, hara el calculo y terminara.

Ejemplo:

```bash
./gradlew :data-center:run --args="monolithic data/datagrams-MiniPilot.csv data/lines-241-ActiveGT.csv results/v1-mini.csv"
```

## 4. Drivers de arquitectura para esta version

Estos drivers se toman de la rubrica y del enunciado.

| Driver | Prioridad | Decision asociada |
|---|---:|---|
| Correctitud del calculo | Alta | Crear una version monolitica secuencial como baseline. |
| Modificabilidad | Alta | Separar lectores, modelos, calculador y escritor de reportes. |
| Rendimiento medible | Media | Registrar tiempo total, registros leidos, registros validos y descartados. |
| Reproducibilidad | Alta | Ejecutar por CLI con rutas de archivos explicitas. |
| Escalabilidad futura | Media | Definir una interfaz `SpeedCalculator` para conectar luego V2 y V3. |
| Trazabilidad experimental | Alta | Generar resultados en `results/` y logs resumidos por ejecucion. |

## 5. Escenarios QAW especificos

### QA-MONO-01 Correctitud

Fuente: Evaluador del proyecto.

Estimulo: Ejecuta la version monolitica con `datagrams-MiniPilot.csv`.

Ambiente: Ejecucion batch local.

Artefacto: `MonolithicSpeedCalculator`.

Respuesta: Se genera un reporte mensual para todas las rutas activas.

Medida: Una muestra calculada manualmente debe coincidir con diferencia maxima de `0.01 km/h`.

### QA-MONO-02 Reproducibilidad

Fuente: Integrante del equipo.

Estimulo: Ejecuta el comando documentado en otra maquina con los mismos archivos.

Ambiente: Java 17 y Gradle.

Artefacto: CLI de `data-center`.

Respuesta: Se genera el mismo CSV de salida.

Medida: Misma cantidad de filas y mismos valores numericos redondeados a dos decimales.

### QA-MONO-03 Modificabilidad

Fuente: Equipo de desarrollo.

Estimulo: Se agrega la version concurrente.

Ambiente: Desarrollo.

Artefacto: Paquete `SITM.analysis`.

Respuesta: La version concurrente reutiliza lectores, modelos, acumuladores y escritor.

Medida: No se duplican parsers CSV ni formato de salida.

### QA-MONO-04 Observabilidad

Fuente: Equipo de pruebas.

Estimulo: Se ejecuta el calculo monolitico.

Ambiente: Dataset MiniPilot.

Artefacto: CLI de analisis.

Respuesta: El sistema imprime metricas de ejecucion.

Medida: Deben aparecer dataset, modo, tiempo total, registros leidos, registros validos, registros descartados y archivo generado.

## 6. Estilos arquitectonicos y patrones de diseno

Esta version aplica el principio KISS: mantener una solucion simple, entendible y facil de validar. No se agregaran abstracciones que no se usen en V1 o que no preparen directamente V2 y V3.

La separacion entre estilos arquitectonicos y patrones de diseno es importante porque la rubrica evalua que esten justificados y alineados con los drivers de arquitectura.

### 6.1 Estilos arquitectonicos

| Estilo arquitectonico | Donde se aplica | Driver que atiende | Justificacion |
|---|---|---|---|
| Arquitectura monolitica | Version 1 completa dentro del modulo `data-center` | Correctitud, simplicidad, reproducibilidad | Para el baseline conviene eliminar red, concurrencia y coordinacion distribuida. Asi el calculo se puede validar sin ruido arquitectonico. |
| Batch Processing | Lectura completa de CSV, procesamiento y escritura de reporte | Rendimiento medible, reproducibilidad | El requerimiento es historico y trabaja sobre archivos completos, no sobre eventos en tiempo real. |
| Capas simples | Separacion logica entre modelos, lectura CSV, calculo y escritura | Modificabilidad, mantenibilidad | Permite cambiar lectura, calculo o salida sin afectar todo el sistema, sin crear una estructura excesiva. |
| Pipe-and-Filter conceptual | Flujo: leer -> validar -> comparar con ultimo datagrama del recorrido -> calcular -> escribir | Correctitud, trazabilidad | Cada etapa transforma datos de forma clara y permite ubicar errores o descartes. |

### 6.2 Patrones de diseno

| Patron de diseno | Donde se aplica | Driver que atiende | Justificacion |
|---|---|---|---|
| Repository | `RouteCsvReader` y `DatagramCsvReader` | Modificabilidad | Aisla el origen de datos. Hoy se leen CSV, pero luego podria cambiarse a base de datos o almacenamiento distribuido. |
| Strategy | Interfaz `SpeedCalculator` | Escalabilidad futura, modificabilidad | Permite implementar V1 monolitica, V2 concurrente y V3 distribuida usando el mismo contrato. |
| DTO / Domain Model | `DatagramRecord`, `ActiveRoute`, `MonthlySpeedReport`, `TrackKey`, `MonthKey` | Correctitud, claridad | Evita pasar arreglos de `String` por todo el sistema y hace explicitas las reglas del dominio. |
| Accumulator / Reducer | `SpeedAccumulator` y combinacion de resultados por `MonthKey` | Correctitud, escalabilidad futura | Centraliza la suma de distancia, tiempo y muestras. El mismo concepto se reutilizara en V2 y V3 para combinar resultados parciales. |
| Command | Modo `monolithic` en `Main` | Reproducibilidad, despliegue | Permite ejecutar el calculo desde CLI con parametros claros sin crear otro proyecto. |
| Value Object | `TrackKey` y `MonthKey` | Correctitud | Evita errores de agrupacion al representar claves compuestas con igualdad y hash bien definidos. |

### 6.3 Decisiones y trade-offs

Decision:

```text
Usar un monolito batch en V1.
```

Ventaja:

```text
Maximiza simplicidad y correctitud. Facilita comparar manualmente resultados.
```

Limitacion:

```text
No escala tan bien como V2 o V3 para datasets grandes.
```

Decision:

```text
Usar capas simples aunque la version sea monolitica.
```

Ventaja:

```text
Evita duplicar codigo cuando se implemente concurrencia y distribucion.
```

Limitacion:

```text
Agrega algunas clases mas que una solucion de un solo archivo, pero evita duplicacion y facilita validar cada responsabilidad.
```

Decision:

```text
Usar Strategy desde V1.
```

Ventaja:

```text
Prepara la arquitectura para V2 y V3 sin reescribir el flujo principal.
```

Limitacion:

```text
En V1 parece mas estructura de la necesaria, pero se justifica por la evolucion obligatoria del proyecto.
```

Decision:

```text
No usar Factory Method todavia.
```

Ventaja:

```text
Mantiene V1 simple. Cuando existan V2 y V3, el selector de estrategias se puede agregar si realmente hace falta.
```

Limitacion:

```text
El `Main` conocera directamente `MonolithicSpeedCalculator` por ahora.
```

## 7. Estructura propuesta de codigo

Crear el paquete:

```text
sitm-mio/data-center/src/main/java/SITM/analysis/
```

Para mantener KISS, no se crearan subpaquetes en V1. Todas las clases de analisis quedaran en `SITM.analysis`.

Clases propuestas:

```text
SITM.analysis.ActiveRoute
SITM.analysis.DatagramRecord
SITM.analysis.TrackKey
SITM.analysis.MonthKey
SITM.analysis.MonthlySpeedReport
SITM.analysis.SpeedAccumulator

SITM.analysis.RouteCsvReader
SITM.analysis.DatagramCsvReader
SITM.analysis.SpeedReportCsvWriter

SITM.analysis.SpeedCalculator
SITM.analysis.MonolithicSpeedCalculator
SITM.analysis.SpeedCalculationResult
SITM.analysis.SpeedCalculationStats
```

Modificar:

```text
sitm-mio/data-center/src/main/java/SITM/Main.java
```

El `Main` decidira:

```text
sin argumentos       -> iniciar servidor Ice actual
modo monolithic      -> ejecutar analisis batch
```

Decision KISS:

```text
No crear `AnalysisCommand` en V1. El parsing simple de argumentos se hara en `Main`.
```

Si luego `Main` crece demasiado al agregar V2 y V3, se extraera `AnalysisCommand`.

## 8. Modelo de datos

### ActiveRoute

Representa una ruta activa del piloto.

Campos:

```text
lineId
planVersionId
shortName
description
activationDate
creationDate
```

Uso:

```text
Filtrar datagramas y asegurar que todas las rutas activas aparezcan en el reporte.
```

### DatagramRecord

Representa una fila valida o parcialmente valida del CSV de datagramas.

Campos:

```text
eventType
registerDate
stopId
odometer
latitude
longitude
taskId
lineId
tripId
unknown1
datagramDate
busId
```

`datagramDate` debe convertirse a `LocalDateTime`.

### TrackKey

Clave para comparar datagramas consecutivos.

Campos:

```text
lineId
busId
tripId
stopId
```

Decision:

Se incluye `stopId` porque el diccionario indica que `odometer` es la distancia desde la ultima parada hasta la ubicacion actual. Cuando cambia la ultima parada, el odometro puede reiniciarse. Comparar odometros entre paradas diferentes puede producir velocidades incorrectas.

### MonthKey

Clave de agregacion mensual.

Campos:

```text
lineId
year
month
```

### SpeedAccumulator

Acumula:

```text
totalDistanceMeters
totalTimeSeconds
samples
discardedPairs
```

La velocidad final se calcula como promedio ponderado:

```text
averageSpeedKmh = (totalDistanceMeters / totalTimeSeconds) * 3.6
```

Decision:

No se promedian velocidades individuales de segmentos porque eso daria el mismo peso a segmentos cortos y largos. Se suma distancia y tiempo para obtener una velocidad promedio ponderada por tiempo/distancia real.

## 9. Reglas de lectura y limpieza de datos

### Rutas activas

Leer `lines-241-ActiveGT.csv`.

Usar:

```text
LINEID
SHORTNAME
DESCRIPTION
```

Guardar rutas en:

```text
Map<Integer, ActiveRoute>
```

### Datagrams

Leer `datagrams-MiniPilot.csv`.

Cada fila debe tener al menos 12 columnas.

Columnas esperadas:

```text
0  eventType
1  registerDate
2  stopId
3  odometer
4  latitude
5  longitude
6  taskId
7  lineId
8  tripId
9  unknown1
10 datagramDate
11 busId
```

### Filtros de datagramas

Aceptar solo datagramas que cumplan:

```text
lineId existe en rutas activas
busId > 0
tripId >= 0
stopId >= 0
odometer >= 0
datagramDate parseable
latitude != -1
longitude != -1
```

Decision sobre `eventType`:

Inicialmente no se filtrara por `eventType` porque el enunciado no especifica cual codigo representa evento GPS. Si el diccionario o el profesor confirma que solo `eventType = 0` representa posicion, se agregara ese filtro.

### Filtros de pares consecutivos

Para dos datagramas consecutivos dentro del mismo `TrackKey`:

```text
deltaDistance = current.odometer - previous.odometer
deltaTimeSeconds = current.datagramDate - previous.datagramDate
```

Aceptar solo si:

```text
deltaDistance >= 0
deltaTimeSeconds > 0
speedKmh <= 100
```

Decision sobre `deltaDistance = 0`:

```text
Se acepta como segmento valido porque representa tiempo transcurrido sin avance. Esto ayuda a que la velocidad promedio refleje detenciones y congestion, no solo movimiento.
```

Decision sobre el limite de 100 km/h:

Se usa como filtro defensivo para descartar datos corruptos o saltos inconsistentes. Es un limite alto para operacion urbana de buses, por lo que no deberia eliminar velocidades realistas.

## 10. Regla de asignacion mensual

Cada delta valido se asignara al mes y ano del datagrama actual:

```text
current.datagramDate.getYear()
current.datagramDate.getMonthValue()
```

Justificacion:

El delta representa la distancia observada al llegar al datagrama actual. Como los datagramas se emiten cada pocos segundos, los pares que cruzan de un mes a otro deberian ser minimos. Esta decision simplifica el baseline y debe documentarse en resultados.

Si se detectan muchos pares cruzando meses, se puede implementar luego una division proporcional del delta entre meses, pero no sera parte obligatoria de la primera version.

## 11. Algoritmo monolitico

Entrada:

```text
datagramFile
routesFile
outputFile
```

Pasos:

```text
1. Iniciar cronometro.
2. Leer rutas activas.
3. Leer datagramas en streaming.
4. Descartar filas invalidas.
5. Para cada datagrama valido, buscar el ultimo datagrama visto con el mismo TrackKey.
6. Calcular delta de distancia y tiempo contra ese datagrama previo.
7. Descartar pares invalidos.
8. Acumular por MonthKey.
9. Actualizar el ultimo datagrama visto para ese TrackKey.
10. Detectar meses presentes en el dataset.
11. Generar reporte para toda combinacion ruta activa + mes presente.
12. Marcar filas sin datos como NO_DATA.
13. Escribir CSV.
14. Imprimir metricas.
```

Pseudocodigo:

```text
routes = readRoutes(routesFile)

accumulators = empty map MonthKey -> SpeedAccumulator
previousByTrack = empty map TrackKey -> DatagramRecord
monthsSeen = empty set year-month

for each valid record read from datagramsFile:
    monthsSeen.add(record.yearMonth)
    trackKey = TrackKey(record.lineId, record.busId, record.tripId, record.stopId)
    previous = previousByTrack[trackKey]
    if previous does not exist:
        previousByTrack[trackKey] = record
        continue

    deltaDistance = record.odometer - previous.odometer
    deltaTime = secondsBetween(previous.datagramDate, record.datagramDate)
    if valid pair:
        key = MonthKey(record.lineId, record.year, record.month)
        accumulators[key].add(deltaDistance, deltaTime)

    if record is newer than previous:
        previousByTrack[trackKey] = record

reports = []
for each month in monthsSeen:
    for each active route:
        key = MonthKey(route.lineId, month.year, month.month)
        accumulator = accumulators.get(key)
        reports.add(build report or NO_DATA)

write reports
```

Decision KISS:

```text
No se carga todo el CSV en memoria. El algoritmo mantiene solo el ultimo datagrama por TrackKey y los acumulados mensuales.
```

Supuesto validado en MiniPilot:

```text
El archivo esta ordenado cronologicamente. La ejecucion reporto 0 filas fuera de orden.
```

## 12. Formato de salida

Archivo:

```text
results/v1-mini.csv
```

Columnas:

```csv
lineId,shortName,description,year,month,averageSpeedKmh,totalDistanceMeters,totalTimeSeconds,samples,status
```

Ejemplo:

```csv
2241,E21,Ruta ejemplo,2019,5,18.42,15320.00,2994.00,81,OK
131,T31,Terminal Paso del Comercio - Universidades,2019,5,0.00,0.00,0.00,0,NO_DATA
```

Orden:

```text
year asc
month asc
lineId asc
```

## 13. Metricas de ejecucion

La ejecucion debe imprimir:

```text
Mode: monolithic
Routes file: ...
Datagrams file: ...
Output file: ...
Active routes: ...
Rows read: ...
Rows accepted: ...
Rows discarded: ...
Track groups: ...
Valid pairs: ...
Discarded pairs: ...
Reports generated: ...
Elapsed ms: ...
```

Estas metricas se usaran en `EXPERIMENT_RESULTS.md`.

## 14. Validacion de correctitud

### Validacion automatica basica

Comprobar:

```text
El archivo de salida existe.
El CSV tiene encabezado.
El CSV tiene filas.
Todas las rutas activas aparecen por cada mes detectado.
No hay velocidades negativas.
No hay velocidades mayores a 100 km/h.
```

### Validacion manual

Seleccionar una muestra pequena:

```text
un lineId
un busId
un tripId
un stopId
5 a 10 datagramas consecutivos
```

Calcular manualmente:

```text
deltaDistance = odometerActual - odometerAnterior
deltaTimeSeconds = fechaActual - fechaAnterior
speedKmh = deltaDistance / deltaTimeSeconds * 3.6
```

Luego comparar contra el acumulado del reporte.

Se recomienda usar archivos del servidor como:

```text
bus1069.csv
bus180.csv
bus421.csv
```

solo para depuracion puntual.

## 15. Plan de pruebas

### Prueba 1: smoke test con `chunck.csv`

Objetivo:

```text
Validar que el comando corre y genera salida con una muestra pequena.
```

Comando esperado:

```bash
./gradlew :data-center:run --args="monolithic data/chunck.csv data/lines-241-ActiveGT.csv results/v1-chunck.csv"
```

### Prueba 2: dataset oficial MiniPilot

Objetivo:

```text
Generar el resultado oficial de la version monolitica.
```

Comando esperado:

```bash
./gradlew :data-center:run --args="monolithic data/datagrams-MiniPilot.csv data/lines-241-ActiveGT.csv results/v1-mini.csv"
```

### Prueba 3: estabilidad de salida

Objetivo:

```text
Ejecutar dos veces y comprobar que el resultado no cambia.
```

Comparacion:

```bash
diff results/v1-mini-run1.csv results/v1-mini-run2.csv
```

En Windows se puede usar:

```powershell
Compare-Object (Get-Content results/v1-mini-run1.csv) (Get-Content results/v1-mini-run2.csv)
```

### Prueba 4: conteo de rutas

Objetivo:

```text
Verificar que todas las rutas activas aparecen en el reporte.
```

Validacion:

```text
filas esperadas = numero_rutas_activas * numero_meses_detectados
```

## 16. Despliegue de la version monolitica

Vista de despliegue:

```text
Maquina local
  |
  | Java 17 + Gradle
  v
data-center modo monolithic
  |
  +-- lee sitm-mio/data/lines-241-ActiveGT.csv
  +-- lee sitm-mio/data/datagrams-MiniPilot.csv
  +-- escribe sitm-mio/results/v1-mini.csv
```

No hay comunicacion por red en esta version.

Justificacion:

Eliminar red, concurrencia y distribucion en V1 permite aislar la correctitud del calculo. Si V1 es incorrecta, V2 y V3 heredarian el error.

## 17. Relacion con la rubrica

| Criterio de rubrica | Como se cubre en V1 |
|---|---|
| Drivers de arquitectura | Se documentan correctitud, modificabilidad, reproducibilidad, observabilidad y rendimiento. |
| Patrones y estilos | Se justifican Batch, Layered Architecture, Repository, Strategy, DTO, Accumulator y Command. |
| Diseno global e integracion | Se ubica el calculo en `data-center`, coherente con analisis historico. |
| Implementacion y validacion experimental | Se define comando, salida, metricas y pruebas con `chunck.csv` y MiniPilot. |
| Visualizacion | No aplica directamente a V1; el resultado CSV puede alimentar visualizaciones posteriores. |

## 18. Riesgos

### Riesgo 1: interpretacion del odometer

El odometro es distancia desde la ultima parada, no odometro global del bus.

Mitigacion:

```text
Comparar solo datagramas con mismo lineId, busId, tripId y stopId.
```

### Riesgo 2: datos invalidos o incompletos

Puede haber coordenadas `-1`, odometros negativos o fechas no parseables.

Mitigacion:

```text
Registrar descartes y no incluir esos datos en el calculo.
```

### Riesgo 3: dataset grande en Git

`datagrams-MiniPilot.csv` y `datagrams4Pilot.csv` no deben subirse al repo publico.

Mitigacion:

```text
Mantenerlos en .gitignore y documentar que se copian desde el servidor SSH.
```

### Riesgo 4: baseline no reproducible

Si la salida depende del orden de mapas no deterministico, podria cambiar entre ejecuciones.

Mitigacion:

```text
Ordenar reportes por year, month y lineId antes de escribir.
```

## 19. Checklist de implementacion

```text
[ ] Confirmar que datagrams-MiniPilot.csv existe en sitm-mio/data/
[ ] Crear paquete SITM.analysis
[ ] Crear modelos de dominio
[ ] Implementar RouteCsvReader
[ ] Implementar DatagramCsvReader
[ ] Implementar SpeedAccumulator
[ ] Implementar SpeedCalculator
[ ] Implementar MonolithicSpeedCalculator
[ ] Implementar SpeedReportCsvWriter
[ ] Modificar data-center Main para aceptar modo monolithic
[ ] Ejecutar smoke test con chunck.csv
[ ] Ejecutar MiniPilot
[ ] Guardar results/v1-mini.csv
[ ] Registrar metricas en EXPERIMENT_RESULTS.md
[ ] Validar una muestra manual
```

## 20. Criterio de finalizacion

La version monolitica estara lista cuando:

- El comando `monolithic` funciona desde `data-center`.
- Se genera `results/v1-mini.csv`.
- El reporte incluye todas las rutas activas por cada mes detectado.
- Las velocidades son no negativas y razonables.
- Se imprimen metricas de ejecucion.
- Existe al menos una validacion manual documentada.
- El diseno queda listo para reutilizar el nucleo en V2 concurrente y V3 distribuida.
