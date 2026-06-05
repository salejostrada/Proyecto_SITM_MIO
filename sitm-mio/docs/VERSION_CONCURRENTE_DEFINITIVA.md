# Version concurrente definitiva - SITM-MIO

## 1. Resumen

La version concurrente calcula velocidades promedio por ruta por mes usando varios hilos dentro de una sola maquina.

Entrada principal de validacion:

```text
data/datagrams-MiniPilot.csv
data/lines-241-ActiveGT.csv
```

Comando de ejecucion:

```powershell
.\gradlew.bat :data-center:run --args="concurrent data/datagrams-MiniPilot.csv data/lines-241-ActiveGT.csv results/v2-mini.csv 8"
```

El ultimo parametro indica la cantidad de hilos de trabajo.

La implementacion esta en:

```text
data-center/src/main/java/SITM/analysis/ConcurrentSpeedCalculator.java
```

Esta version reutiliza lectores, modelos, acumuladores y escritor CSV de la version monolitica. Su objetivo es mejorar el rendimiento manteniendo el mismo contrato de salida.

## 2. Relacion con la rubrica

| Criterio de rubrica | Como se atiende en la version concurrente |
|---|---|
| Drivers de arquitectura y atributos de calidad | Se documentan rendimiento, correctitud, modificabilidad, observabilidad y escalabilidad local. |
| Patrones y estilos arquitectonicos | Se aplican batch processing, master-worker local, strategy, producer-consumer, accumulator/reducer y value object. |
| Diseno global e integracion | La version se integra en `data-center` como modo `concurrent`, sin cambiar la interfaz de salida CSV. |
| Implementacion, despliegue y validacion experimental | La version compila y procesa `datagrams-MiniPilot.csv` con multiples hilos. |
| Comparacion con V1 | Se puede comparar contra la version monolitica porque implementa `SpeedCalculator` y escribe el mismo formato. |

## 3. Drivers de arquitectura

| Driver | Prioridad | Decision tomada |
|---|---:|---|
| Rendimiento | Alta | Usar varios workers para procesar datagramas en paralelo. |
| Correctitud | Alta | Particionar por `lineId`, `busId` y `tripId` para que los datagramas relacionados lleguen al mismo worker. |
| Reproducibilidad | Alta | Mantener entrada, salida y parametros explicitos por CLI. |
| Modificabilidad | Media | Reutilizar `SpeedCalculator`, `DatagramCsvReader`, `RouteCsvReader`, `SpeedAccumulator` y `SpeedReportCsvWriter`. |
| Observabilidad | Media | Imprimir filas leidas, aceptadas, pares validos, descartes y tiempo total. |
| Escalabilidad local | Media | Permitir configurar el numero de hilos sin cambiar codigo. |

## 4. Escenarios QAW

### QAW-01 Rendimiento local

Fuente: equipo de desarrollo.

Estimulo: ejecutar la version concurrente con `datagrams-MiniPilot.csv`.

Ambiente: una maquina local con varios nucleos.

Artefacto: `ConcurrentSpeedCalculator`.

Respuesta: el dataset se procesa con varios workers.

Medida: se reporta `Elapsed ms` y se puede comparar con la version monolitica.

### QAW-02 Correctitud de particion

Fuente: evaluador del proyecto.

Estimulo: procesar datagramas de una misma ruta, bus y viaje.

Ambiente: ejecucion batch concurrente.

Artefacto: particionador interno por hash.

Respuesta: todos los datagramas de la misma clave base llegan al mismo worker.

Medida: no se calculan pares usando estados parciales repartidos entre workers distintos.

### QAW-03 Configurabilidad

Fuente: integrante del equipo.

Estimulo: cambiar el numero de hilos desde el comando.

Ambiente: CLI de `data-center`.

Artefacto: argumento `concurrent ... <numThreads>`.

Respuesta: la version corre con el numero de workers indicado.

Medida: no requiere recompilar ni modificar codigo.

### QAW-04 Observabilidad

Fuente: equipo de pruebas.

Estimulo: ejecutar la version concurrente.

Ambiente: dataset MiniPilot.

Artefacto: salida por consola.

Respuesta: el sistema imprime metricas de lectura, descartes, pares y tiempo total.

Medida: las metricas permiten comparar contra V1 y detectar diferencias de calidad.

## 5. Estilos arquitectonicos

| Estilo | Aplicacion | Justificacion |
|---|---|---|
| Batch Processing | Lee CSV completo, procesa y escribe un reporte final. | El requerimiento es historico, no de tiempo real. |
| Master-Worker local | El hilo principal lee y reparte datagramas; los workers procesan particiones. | Permite paralelizar el calculo dentro de una maquina. |
| Producer-Consumer | El productor inserta datagramas en colas bloqueantes; los consumidores los procesan. | Desacopla lectura y procesamiento. |
| Reducer final | El hilo principal combina acumuladores parciales por `MonthKey`. | Evita compartir acumuladores globales durante el procesamiento paralelo. |

## 6. Patrones de diseno

| Patron | Clase o lugar | Justificacion |
|---|---|---|
| Strategy | `SpeedCalculator` | Permite usar V1, V2 y V3 bajo el mismo contrato. |
| Master-Worker | `ConcurrentSpeedCalculator` y clase interna `Worker` | Separa coordinacion y procesamiento parcial. |
| Producer-Consumer | `LinkedBlockingQueue<DatagramRecord>` | El lector produce registros y cada worker consume su cola. |
| Accumulator / Reducer | `SpeedAccumulator` y merge final | Cada worker acumula localmente y luego se combinan resultados. |
| Value Object | `TrackKey`, `MonthKey` | Representan claves compuestas con igualdad y orden definidos. |
| Command | modo `concurrent` en `Main` | Permite ejecutar la version desde CLI con argumentos explicitos. |

## 7. Decision de diseno principal

La version concurrente no divide el archivo CSV por rangos de lineas. En su lugar, lee el archivo en un hilo productor y reparte cada datagrama validado segun:

```text
partition = hash(lineId, busId, tripId) % numThreads
```

Justificacion:

```text
Si el archivo se partiera por bloques de lineas, datagramas relacionados podrian quedar en workers distintos y se perderia el ultimo datagrama visto para calcular el siguiente par.
```

La clave de particion no incluye `stopId`, pero el `TrackKey` usado dentro del worker si lo incluye:

```text
TrackKey = lineId + busId + tripId + stopId
```

Esto mantiene en el mismo worker todas las paradas de un mismo viaje base y conserva la posibilidad de calcular por `TrackKey`.

## 8. Funcionamiento

Entrada:

```text
data/datagrams-MiniPilot.csv
data/lines-241-ActiveGT.csv
```

Salida:

```text
results/v2-mini.csv
```

Flujo:

```text
1. Leer rutas activas.
2. Crear un ExecutorService con numThreads.
3. Crear un Worker por hilo.
4. Leer datagramas en streaming con DatagramCsvReader.
5. Validar rutas activas y valores requeridos.
6. Calcular particion con hash(lineId, busId, tripId).
7. Encolar el datagrama en la cola del worker correspondiente.
8. Cada worker procesa sus datagramas en orden de llegada.
9. Cada worker mantiene previousByTrack y acumuladores por MonthKey.
10. Al terminar la lectura, el productor envia una senal de parada a cada worker.
11. El hilo principal espera a que los workers terminen.
12. Se combinan acumuladores parciales.
13. Se genera el reporte CSV.
```

## 9. Formula de calculo

Para cada par valido de datagramas consecutivos del mismo `TrackKey` dentro de un worker:

```text
deltaDistance = current.odometer - previous.odometer
deltaTimeSeconds = current.datagramDate - previous.datagramDate
```

Luego:

```text
speedKmh = (deltaDistance / deltaTimeSeconds) * 3.6
```

La velocidad promedio mensual se calcula con acumulados:

```text
averageSpeedKmh = (totalDistanceMeters / totalTimeSeconds) * 3.6
```

Esto evita promediar velocidades individuales con el mismo peso.

## 10. Reglas de limpieza actuales

Se aceptan datagramas si cumplen:

```text
lineId pertenece a rutas activas
busId > 0
tripId >= 0
stopId >= 0
odometer >= 0
latitude != -1
longitude != -1
datagramDate parseable
```

Se aceptan pares si cumplen:

```text
deltaDistance >= 0
deltaTimeSeconds > 0
deltaTimeSeconds <= 900
speedKmh <= 100
```

Decision sobre `deltaTimeSeconds <= 900`:

```text
Se usa el mismo limite de 15 minutos de la version monolitica corregida. Esta regla evita unir datagramas de servicios distintos que reutilizan lineId, busId, tripId y stopId pero estan separados por horas o dias.
```

## 11. Implementacion realizada

Archivo principal:

```text
data-center/src/main/java/SITM/analysis/ConcurrentSpeedCalculator.java
```

Archivos reutilizados:

```text
data-center/src/main/java/SITM/analysis/ActiveRoute.java
data-center/src/main/java/SITM/analysis/DatagramRecord.java
data-center/src/main/java/SITM/analysis/TrackKey.java
data-center/src/main/java/SITM/analysis/MonthKey.java
data-center/src/main/java/SITM/analysis/MonthlySpeedReport.java
data-center/src/main/java/SITM/analysis/SpeedAccumulator.java
data-center/src/main/java/SITM/analysis/SpeedCalculationStats.java
data-center/src/main/java/SITM/analysis/SpeedCalculationResult.java
data-center/src/main/java/SITM/analysis/SpeedCalculator.java
data-center/src/main/java/SITM/analysis/CsvSupport.java
data-center/src/main/java/SITM/analysis/RouteCsvReader.java
data-center/src/main/java/SITM/analysis/DatagramCsvReader.java
data-center/src/main/java/SITM/analysis/SpeedReportCsvWriter.java
```

Integracion CLI:

```text
data-center/src/main/java/SITM/Main.java
```

Modo:

```text
concurrent
```

Uso:

```text
concurrent <datagramsFile> <routesFile> <outputFile> <numThreads>
```

## 12. Concurrencia y sincronizacion

Cada worker mantiene estado local:

```text
previousByTrack: TrackKey -> ultimo DatagramRecord
accumulators: MonthKey -> SpeedAccumulator
monthsSeen: meses observados por el worker
stats: metricas parciales
```

El hilo principal no modifica esos mapas mientras el worker procesa. Al final, despues de `awaitTermination`, combina los resultados.

Mecanismo de parada:

```text
POISON_PILL
```

Cada cola recibe un datagrama sentinela para indicar que el worker debe terminar.

Capacidad de cola:

```text
QUEUE_CAPACITY = 50000
```

Esto evita crecimiento ilimitado de memoria si los workers procesan mas lento que el lector.

## 13. Formato de salida

Columnas:

```csv
lineId,shortName,description,year,month,averageSpeedKmh,totalDistanceMeters,totalTimeSeconds,samples,status
```

Ejemplo de salida de V2 con MiniPilot:

```csv
131,T31,Terminal Paso del Comercio - Universidades,2019,5,16.50,13985895.00,3051338.00,133537,OK
140,T40,Terminal Andres Sanin - Centro,2019,5,15.42,6458017.00,1507333.00,69331,OK
150,T50,Estacion Nuevo Latir - Centro,2019,5,15.80,4706447.00,1072203.00,54204,OK
```

## 14. Resultados de validacion

### Compilacion

Comando ejecutado:

```powershell
.\gradlew.bat :data-center:compileJava
```

Resultado:

```text
BUILD SUCCESSFUL
```

### Ejecucion concurrente con MiniPilot

Comando ejecutado:

```powershell
$out = Join-Path $env:TEMP 'sitm-mio-v2-mini-review.csv'
.\gradlew.bat :data-center:run --args="concurrent data/datagrams-MiniPilot.csv data/lines-241-ActiveGT.csv $out 8"
```

Resultado:

```text
Processing datagrams with 8 threads...
Rows accepted: 1000000...
Rows accepted: 2000000...
Rows accepted: 3000000...
Rows accepted: 4000000...
Rows accepted: 5000000...
Finished reading file. Draining queues...
Merging results...
Mode: concurrent
Active routes: 111
Rows read: 8145462
Rows accepted: 5759049
Rows discarded: 2386413
Rows discarded by invalid column count: 0
Rows discarded by parse error: 0
Rows discarded by inactive route: 0
Rows discarded by invalid values: 2386413
Track groups: 962251
Valid pairs: 3943978
Discarded pairs: 852820
Pairs discarded by negative distance: 510188
Pairs discarded by invalid time: 263175
Pairs discarded by excessive time gap: 33334
Pairs discarded by unrealistic speed: 46123
Out-of-order rows observed: 0
Reports generated: 111
Elapsed ms: 51861
BUILD SUCCESSFUL
```

### Revision de calidad del resultado

```text
Rutas OK: 95
Rutas OK con velocidad mayor a 0 y menor a 4 km/h: 1
Rutas OK con velocidad mayor a 0 y menor a 1 km/h: 1
```

Comparacion contra V1 corregida:

```text
V1 corregida y V2 corregida descartan pares con deltaTimeSeconds > 900.
Con MiniPilot, ambos CSV son equivalentes.
T50 queda en 15.80 km/h en ambas versiones.
```

Verificacion de equivalencia:

```text
git diff --no-index -- sitm-mio/results/v1-mini.csv sitm-mio/results/v2-mini.csv
```

Resultado:

```text
Sin diferencias.
```

## 15. Comparacion con la version monolitica

Coincidencias de diseno:

```text
- Mismo formato de entrada.
- Mismo formato de salida.
- Misma formula de velocidad promedio.
- Mismos modelos de dominio.
- Mismo escritor CSV.
- Misma interfaz SpeedCalculator.
```

Diferencias:

```text
- V1 procesa en un solo hilo.
- V2 usa un productor y varios workers.
- V2 mantiene acumuladores parciales por worker.
- V2 combina resultados al final.
- V2 aplica la misma regla temporal de V1 corregida, pero la ejecuta dentro de cada worker.
```

Resultado de aceptacion funcional:

```text
Con MiniPilot, results/v2-mini.csv coincide con results/v1-mini.csv.
```

## 16. Deployment de la version concurrente

Vista de despliegue:

```text
Maquina local
  |
  | Java 17 + Gradle
  v
data-center modo concurrent
  |
  +-- hilo productor lee data/lines-241-ActiveGT.csv
  +-- hilo productor lee data/datagrams-MiniPilot.csv
  +-- workers procesan particiones en paralelo
  +-- hilo principal combina resultados
  +-- escribe CSV de salida
```

No requiere red ni procesos remotos.

Comando de despliegue empaquetado:

```bash
JAVA_OPTS="-Xmx8g" ./bin/data-center concurrent /opt/sitm-mio/datagrams4Pilot.csv /opt/sitm-mio/lines-241-ActiveGT.csv resultados_concurrent.csv 4
```

## 17. Checklist de revision

```text
[x] Confirmar existencia de ConcurrentSpeedCalculator.
[x] Confirmar integracion desde Main con modo concurrent.
[x] Confirmar compilacion.
[x] Ejecutar V2 con MiniPilot y 8 threads.
[x] Registrar metricas principales.
[x] Revisar si el formato CSV coincide con V1.
[x] Detectar diferencia de reglas frente a V1 corregida.
[x] Alinear V2 con la regla deltaTimeSeconds <= 900.
[x] Reejecutar V2 despues de la alineacion.
[x] Comparar CSV de V2 alineada contra V1 corregida.
[ ] Medir rendimiento con varios numeros de hilos.
```

## 18. Limitaciones

- La ejecucion local revisada con 8 hilos fue mas lenta que la V1 corregida en esta maquina. Esto puede deberse a que la lectura del CSV sigue siendo un cuello de botella, al costo de colas bloqueantes y al overhead de coordinacion.
- La cantidad de hilos se recibe por argumento, pero no hay validacion defensiva para valores menores o iguales a cero.
- El uso de `Math.abs(h % numThreads)` puede fallar en casos extremos si el hash produce `Integer.MIN_VALUE`, aunque es poco probable.
- El archivo `results/resultado_v2.csv` existente contiene resultados de 2018 generados antes de esta revision; la evidencia corregida de MiniPilot queda en `results/v2-mini.csv`.

## 19. Conclusion

La version concurrente cumple el objetivo funcional de V2:

```text
Procesa el dataset en una sola maquina usando multiples hilos, mantiene el formato de salida de V1 y produce resultados equivalentes al baseline monolitico corregido.
```

Queda pendiente evaluar rendimiento con varios numeros de hilos:

```text
La validacion funcional ya paso con MiniPilot; la mejora de tiempo debe medirse con datasets mas grandes y configuraciones de hilos diferentes.
```

Con esta alineacion, V2 ya puede usarse para comparar rendimiento de forma valida contra el baseline monolitico corregido.
