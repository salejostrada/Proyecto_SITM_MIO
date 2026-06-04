# Version monolitica definitiva - SITM-MIO

## 1. Resumen

La version monolitica calcula las velocidades promedio por ruta por mes usando el dataset piloto:

```text
data/datagrams-MiniPilot.csv
```

y la lista de rutas activas:

```text
data/lines-241-ActiveGT.csv
```

Esta version es el baseline del proyecto. Su objetivo principal es entregar un resultado correcto, reproducible y facil de validar antes de implementar la version concurrente y la version distribuida.

Resultado generado:

```text
results/v1-mini.csv
```

Comando de ejecucion:

```powershell
.\gradlew.bat :data-center:run --args="monolithic data/datagrams-MiniPilot.csv data/lines-241-ActiveGT.csv results/v1-mini.csv"
```

## 2. Relacion con la rubrica

| Criterio de rubrica | Como se atiende en la version monolitica |
|---|---|
| Drivers de arquitectura y atributos de calidad | Se documentan correctitud, reproducibilidad, modificabilidad, rendimiento medible y trazabilidad. |
| Patrones y estilos arquitectonicos | Se aplican y justifican arquitectura monolitica, batch processing, capas simples, pipe-and-filter conceptual, repository, strategy, DTO, accumulator/reducer, command y value object. |
| Diseno global e integracion | La solucion se integra en `data-center`, que es el modulo responsable del analisis historico. No rompe el flujo Ice existente. |
| Implementacion, despliegue y validacion experimental | La version se implemento, compilo y ejecuto con `chunck.csv` y `datagrams-MiniPilot.csv`. Se generaron metricas y archivo CSV final. |
| Visualizacion | La V1 no implementa mapa ni UI. Entrega un CSV consumible por una futura visualizacion o por analisis manual. |

## 3. Drivers de arquitectura

| Driver | Prioridad | Decision tomada |
|---|---:|---|
| Correctitud | Alta | Crear primero una version secuencial simple para usar como baseline. |
| Reproducibilidad | Alta | Ejecutar por comando con archivos de entrada y salida explicitos. |
| Modificabilidad | Alta | Separar lectura, modelos, calculo y escritura. |
| Rendimiento medible | Media | Imprimir metricas de filas, pares validos, descartes y tiempo. |
| Escalabilidad futura | Media | Definir `SpeedCalculator` para reutilizar el contrato en V2 y V3. |
| Trazabilidad | Alta | Guardar resultados en `results/` y reportar estadisticas por consola. |

## 4. Escenarios QAW

### QAW-01 Correctitud del calculo

Fuente: evaluador del proyecto.

Estimulo: ejecutar la version monolitica con `datagrams-MiniPilot.csv`.

Ambiente: ejecucion batch local.

Artefacto: `MonolithicSpeedCalculator`.

Respuesta: se genera un CSV con velocidad promedio mensual para cada ruta activa.

Medida: no hay velocidades negativas; las rutas activas aparecen en el reporte; las muestras manuales deben coincidir con el calculo esperado.

### QAW-02 Reproducibilidad

Fuente: integrante del equipo.

Estimulo: ejecutar el comando documentado en otra maquina con los mismos datos.

Ambiente: Java 17 y Gradle.

Artefacto: `data-center`.

Respuesta: se genera el mismo archivo de salida.

Medida: misma cantidad de filas y mismos valores redondeados a dos decimales.

### QAW-03 Modificabilidad

Fuente: equipo de desarrollo.

Estimulo: agregar la version concurrente.

Ambiente: desarrollo.

Artefacto: paquete `SITM.analysis`.

Respuesta: V2 puede reutilizar lectores, modelos, acumuladores y escritor CSV.

Medida: no se duplican parsers ni formato de salida.

### QAW-04 Observabilidad

Fuente: equipo de pruebas.

Estimulo: ejecutar el calculo monolitico.

Ambiente: dataset MiniPilot.

Artefacto: CLI de `data-center`.

Respuesta: el sistema imprime metricas de ejecucion.

Medida: se reportan filas leidas, aceptadas, descartadas, pares validos, pares descartados, reportes generados y tiempo total.

## 5. Estilos arquitectonicos

| Estilo | Aplicacion | Justificacion |
|---|---|---|
| Arquitectura monolitica | Toda la V1 corre en un solo proceso dentro de `data-center`. | Reduce complejidad y permite validar el calculo antes de agregar concurrencia o distribucion. |
| Batch Processing | Lee CSV, procesa lote completo y escribe CSV de salida. | El requerimiento es historico, no de tiempo real. |
| Capas simples | Modelos, lectores, calculador y escritor estan separados. | Mantiene KISS sin convertir V1 en una arquitectura sobredisenada. |
| Pipe-and-Filter conceptual | leer -> validar -> comparar -> acumular -> reportar. | Facilita explicar el flujo y rastrear descartes. |

## 6. Patrones de diseno

| Patron | Clase o lugar | Justificacion |
|---|---|---|
| Repository | `RouteCsvReader`, `DatagramCsvReader` | Aisla el acceso a archivos CSV. |
| Strategy | `SpeedCalculator` | Permite que V1, V2 y V3 compartan el mismo contrato de calculo. |
| DTO / Domain Model | `ActiveRoute`, `DatagramRecord`, `MonthlySpeedReport` | Evita trabajar con arreglos de strings durante el calculo. |
| Value Object | `TrackKey`, `MonthKey` | Representa claves compuestas con igualdad y hash correctos. |
| Accumulator / Reducer | `SpeedAccumulator` | Centraliza suma de distancia, tiempo y muestras. |
| Command | argumento `monolithic` en `Main` | Permite ejecutar la version desde un solo comando. |

## 7. Decision de diseno principal

La version monolitica se implemento dentro de:

```text
data-center
```

Justificacion:

```text
El data-center representa el componente de almacenamiento y analisis historico.
El calculo de velocidades promedio por mes es una consulta historica, no una simulacion de tiempo real.
```

Tambien se decidio no usar `bus-simulator` en V1.

Justificacion:

```text
bus-simulator envia eventos con retrasos artificiales para simular tiempo real.
La V1 necesita procesar el dataset historico completo de forma directa y medible.
```

## 8. Funcionamiento

Entrada:

```text
data/datagrams-MiniPilot.csv
data/lines-241-ActiveGT.csv
```

Salida:

```text
results/v1-mini.csv
```

Flujo:

```text
1. Leer rutas activas.
2. Leer datagramas en streaming.
3. Validar que el datagrama pertenezca a una ruta activa.
4. Descartar datos invalidos.
5. Identificar el recorrido con TrackKey.
6. Comparar con el ultimo datagrama visto para ese TrackKey.
7. Calcular distancia y tiempo.
8. Descartar pares invalidos, saltos temporales excesivos o velocidades absurdas.
9. Acumular distancia y tiempo por ruta, ano y mes.
10. Generar el reporte CSV.
```

Clave usada para comparar datagramas:

```text
TrackKey = lineId + busId + tripId + stopId
```

Justificacion:

```text
El odometer representa distancia desde la ultima parada. Por eso se incluye stopId; comparar odometros entre paradas distintas puede producir resultados incorrectos.
Como tripId puede reutilizarse en dias diferentes, el TrackKey se complementa con una regla de continuidad temporal para no unir servicios separados.
```

Clave de agregacion:

```text
MonthKey = lineId + year + month
```

## 9. Formula de calculo

Para cada par valido de datagramas consecutivos del mismo `TrackKey`:

```text
deltaDistance = current.odometer - previous.odometer
deltaTimeSeconds = current.datagramDate - previous.datagramDate
```

Luego:

```text
speedKmh = (deltaDistance / deltaTimeSeconds) * 3.6
```

La velocidad promedio mensual no se calcula promediando velocidades individuales. Se calcula con acumulados:

```text
averageSpeedKmh = (totalDistanceMeters / totalTimeSeconds) * 3.6
```

Justificacion:

```text
Este promedio pondera correctamente la distancia y el tiempo. Un promedio simple de velocidades daria el mismo peso a tramos cortos y largos.
```

## 10. Reglas de limpieza

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

Decision sobre `deltaDistance = 0`:

```text
Se acepta porque representa tiempo transcurrido sin avance. Esto permite que detenciones y congestion afecten la velocidad promedio.
```

Decision sobre `speedKmh <= 100`:

```text
Se usa como filtro defensivo para descartar saltos anormales. Es un limite alto para operacion urbana de buses.
```

Decision sobre `deltaTimeSeconds <= 900`:

```text
Se usa un limite de 15 minutos para evitar unir datagramas de servicios distintos. En el dataset MiniPilot se encontraron pares con la misma clave logica separados por 1, 2 o 3 dias; aceptarlos inflaba totalTimeSeconds y reducia artificialmente averageSpeedKmh.
```

## 11. Implementacion realizada

Archivos nuevos:

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
data-center/src/main/java/SITM/analysis/MonolithicSpeedCalculator.java
```

Archivos modificados:

```text
data-center/src/main/java/SITM/Main.java
data-center/build.gradle
.gitignore
```

## 12. Comandos

Compilar:

```powershell
.\gradlew.bat :data-center:compileJava
```

Ejecutar prueba pequena:

```powershell
.\gradlew.bat :data-center:run --args="monolithic data/chunck.csv data/lines-241-ActiveGT.csv results/v1-chunck.csv"
```

Ejecutar version oficial MiniPilot:

```powershell
.\gradlew.bat :data-center:run --args="monolithic data/datagrams-MiniPilot.csv data/lines-241-ActiveGT.csv results/v1-mini.csv"
```

Ejecutar `data-center` como servidor Ice, comportamiento anterior:

```powershell
.\gradlew.bat :data-center:run
```

## 13. Formato de salida

Archivo:

```text
results/v1-mini.csv
```

Columnas:

```csv
lineId,shortName,description,year,month,averageSpeedKmh,totalDistanceMeters,totalTimeSeconds,samples,status
```

Ejemplo de salida:

```csv
131,T31,Terminal Paso del Comercio - Universidades,2019,5,16.50,13985895.00,3051338.00,133537,OK
140,T40,Terminal Andres Sanin - Centro,2019,5,15.42,6458017.00,1507333.00,69331,OK
```

Campos principales:

```text
averageSpeedKmh: velocidad promedio mensual en km/h.
samples: cantidad de pares validos usados.
status: OK o NO_DATA.
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

### Smoke test con chunck.csv

Comando ejecutado:

```powershell
.\gradlew.bat :data-center:run --args="monolithic data/chunck.csv data/lines-241-ActiveGT.csv results/v1-chunck.csv"
```

Resultado:

```text
Rows read: 100
Rows accepted: 82
Rows discarded: 18
Valid pairs: 6
Discarded pairs: 5
Reports generated: 111
Elapsed ms: 69
BUILD SUCCESSFUL
```

### Ejecucion oficial con MiniPilot

Comando ejecutado:

```powershell
.\gradlew.bat :data-center:run --args="monolithic data/datagrams-MiniPilot.csv data/lines-241-ActiveGT.csv results/v1-mini.csv"
```

Resultado:

```text
Rows read: 8145462
Rows accepted: 5759049
Rows discarded: 2386413
Track groups: 962251
Valid pairs: 3943978
Discarded pairs: 852820
Pairs discarded by negative distance: 510188
Pairs discarded by invalid time: 263175
Pairs discarded by excessive time gap: 33334
Pairs discarded by unrealistic speed: 46123
Out-of-order rows observed: 0
Reports generated: 111
Elapsed ms: 39876
BUILD SUCCESSFUL
```

Revision de calidad del resultado:

```text
Antes del filtro temporal habia 43 rutas OK con velocidad promedio mayor a 0 y menor a 4 km/h.
Despues del filtro temporal queda 1 ruta OK por debajo de 4 km/h.
Ejemplo: T50 pasa de 1.90 km/h a 15.80 km/h porque se dejan de acumular gaps de dias entre datagramas que no pertenecen al mismo servicio continuo.
```

## 15. Deployment de la version monolitica

Vista de despliegue:

```text
Maquina local
  |
  | Java 17 + Gradle
  v
data-center modo monolithic
  |
  +-- lee data/lines-241-ActiveGT.csv
  +-- lee data/datagrams-MiniPilot.csv
  +-- escribe results/v1-mini.csv
```

No hay red ni procesos remotos en esta version.

Justificacion:

```text
La V1 busca aislar correctitud. La red, concurrencia y distribucion se agregan despues.
```

## 16. Checklist de implementacion

```text
[x] Confirmar que datagrams-MiniPilot.csv existe en sitm-mio/data/
[x] Crear paquete SITM.analysis
[x] Crear modelos de dominio
[x] Implementar RouteCsvReader
[x] Implementar DatagramCsvReader
[x] Implementar SpeedAccumulator
[x] Implementar SpeedCalculator
[x] Implementar MonolithicSpeedCalculator
[x] Implementar SpeedReportCsvWriter
[x] Modificar data-center Main para aceptar modo monolithic
[x] Configurar data-center con plugin application
[x] Configurar run con workingDir en la raiz del proyecto
[x] Actualizar .gitignore para no subir datasets grandes
[x] Ejecutar compilacion
[x] Ejecutar smoke test con chunck.csv
[x] Ejecutar MiniPilot
[x] Generar results/v1-mini.csv
[x] Registrar metricas principales
```

Pendiente recomendado para el documento de resultados final:

```text
[ ] Hacer validacion manual detallada de una muestra pequena
[ ] Copiar las metricas a EXPERIMENT_RESULTS.md cuando se comparen V1, V2 y V3
```

## 17. Limitaciones

- Esta version no usa concurrencia.
- Esta version no usa distribucion.
- Esta version no implementa visualizacion.
- El archivo `datagrams4Pilot.csv` no es necesario para V1; se usara despues para pruebas de rendimiento.
- El limite de 15 minutos es una regla defensiva de continuidad temporal. Si el dominio define otra frecuencia maxima entre datagramas consecutivos, el umbral debe ajustarse y volver a ejecutar la validacion.
- Si en otro dataset las filas no estan ordenadas cronologicamente, puede ser necesario ordenar por `TrackKey` y fecha o ajustar el algoritmo.

## 18. Conclusion

La version monolitica cumple el objetivo inicial del proyecto:

```text
Calcular velocidades promedio por ruta por mes para todas las rutas activas del piloto usando datagrams-MiniPilot.csv.
```

Ademas, deja una base reutilizable para las siguientes versiones:

- V2 concurrente: puede procesar particiones usando los mismos modelos, lectores y acumuladores.
- V3 distribuida: puede reutilizar el mismo concepto de acumulados parciales y reduccion final.
