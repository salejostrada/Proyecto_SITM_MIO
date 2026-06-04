# Guia-plan del proyecto SITM-MIO

## 1. Objetivo del proyecto

Calcular la velocidad promedio mensual de todas las rutas activas del piloto SITM-MIO usando los datagramas historicos de buses.

La solucion debe evolucionar en tres versiones:

1. Version monolitica.
2. Version concurrente en una sola maquina.
3. Version distribuida usando un patron de distribucion.

Ademas, se deben entregar:

- Drivers de arquitectura con escenarios QAW.
- Deployment con patrones usados.
- Implementacion.
- Documento de resultados del experimento.

## 2. Datos de entrada

Los archivos esperados por la consigna son:

```text
/opt/sitm-mio/lines-241-ActiveGT.csv
/opt/sitm-mio/datagrams-MiniPilot.csv
/opt/sitm-mio/datagrams4Pilot.csv
/opt/sitm-mio/Diccionario_De_Datos-OkGTM.pdf
```

En este repositorio ya existen:

```text
sitm-mio/data/lines-241-ActiveGT.csv
sitm-mio/data/chunck.csv
sitm-mio/docs/Diccionario_De_Datos-OkGTM.pdf
```

Pendiente por copiar al computador local, si se quiere ejecutar y medir desde este repo:

```text
sitm-mio/data/datagrams-MiniPilot.csv
sitm-mio/data/datagrams4Pilot.csv
```

Mientras no esten los archivos oficiales, `sitm-mio/data/chunck.csv` puede usarse solo como muestra pequena para probar lectores y flujo basico.

## 3. Preparacion de datos desde el servidor SSH

Los datos oficiales estan disponibles en el servidor:

```text
swarch@206m03:/opt/sitm-mio
```

Archivos vistos en el servidor:

```text
1171chunck.csv
bus1069.csv
bus180.csv
bus421.csv
chunkc75.csv
datagrams-MiniPilot.csv
datagrams-MiniPilot.zip
datagrams4Pilot.csv
datagrams4Pilot.zip
Diccionario_De_Datos-OkGTM.pdf
ISW4-ProyFinal-EnunciadoBase.pdf
lines-241-ActiveGT.csv
```

Uso recomendado de cada archivo:

- `lines-241-ActiveGT.csv`: lista oficial de rutas activas. Es obligatorio para filtrar rutas.
- `datagrams-MiniPilot.csv`: dataset pequeno oficial. Usarlo primero para implementar y validar correctitud.
- `datagrams4Pilot.csv`: dataset nueve veces mas grande. Usarlo despues para pruebas de rendimiento.
- `Diccionario_De_Datos-OkGTM.pdf`: explica columnas, tipos y rangos de los datagramas.
- `ISW4-ProyFinal-EnunciadoBase.pdf`: enunciado completo del proyecto.
- `bus1069.csv`, `bus180.csv`, `bus421.csv`: muestras por bus. Sirven para depurar calculos de un bus especifico.
- `1171chunck.csv`, `chunkc75.csv`: muestras pequenas. Sirven para pruebas rapidas, no para resultados finales.
- Archivos `.zip`: respaldo comprimido de los CSV grandes.

### Decision practica

Si se va a desarrollar desde el computador local, lo correcto es traer al repo los archivos oficiales antes de iniciar las mediciones.

Primero copiar solo el dataset pequeno:

```powershell
scp swarch@206m03:/opt/sitm-mio/datagrams-MiniPilot.csv .\sitm-mio\data\
```

Luego copiar el dataset grande:

```powershell
scp swarch@206m03:/opt/sitm-mio/datagrams4Pilot.csv .\sitm-mio\data\
```

Si tambien se quiere actualizar la copia local de rutas y documentos:

```powershell
scp swarch@206m03:/opt/sitm-mio/lines-241-ActiveGT.csv .\sitm-mio\data\
scp swarch@206m03:/opt/sitm-mio/Diccionario_De_Datos-OkGTM.pdf .\sitm-mio\docs\
scp swarch@206m03:/opt/sitm-mio/ISW4-ProyFinal-EnunciadoBase.pdf .\sitm-mio\docs\
```

Si el nombre `206m03` no resuelve desde el computador local, usar la IP o el host completo que se uso para entrar por SSH.

### Verificacion despues de copiar

Desde PowerShell:

```powershell
Get-ChildItem .\sitm-mio\data\datagrams*.csv
(Get-Content .\sitm-mio\data\datagrams-MiniPilot.csv | Measure-Object -Line).Lines
(Get-Content .\sitm-mio\data\datagrams4Pilot.csv | Measure-Object -Line).Lines
```

Desde el servidor SSH tambien se pueden revisar tamanos antes de copiar:

```bash
cd /opt/sitm-mio
ls -lh datagrams-MiniPilot.csv datagrams4Pilot.csv lines-241-ActiveGT.csv
wc -l datagrams-MiniPilot.csv datagrams4Pilot.csv lines-241-ActiveGT.csv
```

### Alternativa: ejecutar en el servidor

Tambien se puede subir el codigo al servidor y ejecutar alla. Esta opcion tiene sentido si:

- Los CSV son muy grandes para copiarlos.
- El computador local es lento.
- La version distribuida se quiere probar con varios procesos en el mismo ambiente Linux.

Para empezar, la opcion mas simple es copiar los datos a `sitm-mio/data/` en el computador local y desarrollar ahi. Despues se puede repetir el experimento en el servidor si se necesita.

## 4. Estructura actual del proyecto

El proyecto esta organizado en modulos Gradle:

```text
sitm-mio/
  contracts/
  bus-simulator/
  event-processor/
  data-center/
  visualizer-client/
  data/
  docs/
```

Responsabilidades actuales:

- `contracts`: define los contratos Ice en `sitm.ice`.
- `bus-simulator`: lee datagramas desde CSV y los envia al procesador.
- `event-processor`: recibe datagramas, normaliza coordenadas y reenvia datos.
- `data-center`: debe almacenar y calcular reportes historicos.
- `visualizer-client`: muestra informacion de buses en tiempo real.

Para esta consigna, el calculo batch debe concentrarse principalmente en `data-center`, sin romper el flujo distribuido existente.

## 5. Definicion del calculo

Se debe calcular velocidad promedio por:

```text
lineId, year, month
```

Campos importantes del datagrama:

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

Formula recomendada:

```text
velocidad_kmh = (distancia_total_metros / tiempo_total_segundos) * 3.6
```

Para evitar calculos incorrectos, la distancia y el tiempo deben calcularse usando pares consecutivos de datagramas de un mismo recorrido:

```text
misma ruta: lineId
mismo bus: busId
mismo viaje: tripId
ordenados por fecha: datagramDate
```

Para cada par consecutivo:

```text
deltaDistancia = odometerActual - odometerAnterior
deltaTiempo = fechaActual - fechaAnterior
```

El par es valido si:

```text
deltaDistancia > 0
deltaTiempo > 0
lineId pertenece a las rutas activas
odometer >= 0
busId > 0
tripId >= 0
datagramDate se puede parsear
```

Tambien se recomienda descartar velocidades absurdas:

```text
velocidad_kmh <= 100
```

Este limite debe documentarse como decision de limpieza de datos.

## 6. Formato esperado de salida

El reporte final deberia generarse como CSV:

```csv
lineId,shortName,year,month,averageSpeedKmh,totalDistanceMeters,totalTimeSeconds,samples,status
2241,A01,2019,5,18.42,15320,2994,81,OK
131,T31,2019,5,0.00,0,0,0,NO_DATA
```

Campos:

- `lineId`: identificador de la ruta.
- `shortName`: nombre corto de la ruta.
- `year`: ano del reporte.
- `month`: mes del reporte.
- `averageSpeedKmh`: velocidad promedio mensual.
- `totalDistanceMeters`: distancia acumulada usada para el calculo.
- `totalTimeSeconds`: tiempo acumulado usado para el calculo.
- `samples`: numero de pares validos procesados.
- `status`: `OK` o `NO_DATA`.

Todas las rutas activas deben aparecer en la salida, incluso si no tienen datagramas validos.

## 7. Version 1: solucion monolitica

### Objetivo

Implementar una version simple, correcta y secuencial usando `datagrams-MiniPilot.csv`.

Esta version sera la referencia para validar que las versiones concurrente y distribuida producen los mismos resultados.

### Paquetes sugeridos

Crear en `data-center`:

```text
sitm-mio/data-center/src/main/java/SITM/analysis/
```

Clases sugeridas:

```text
ActiveRoute.java
DatagramRecord.java
MonthlySpeedReport.java
RouteReader.java
DatagramReader.java
SpeedAccumulator.java
SpeedCalculator.java
MonolithicSpeedCalculator.java
SpeedReportWriter.java
AnalysisMain.java
```

### Flujo

1. Leer `lines-241-ActiveGT.csv`.
2. Guardar las rutas activas en memoria.
3. Leer `datagrams-MiniPilot.csv`.
4. Filtrar datagramas cuyo `lineId` este en las rutas activas.
5. Agrupar por `lineId`, `busId`, `tripId`.
6. Ordenar cada grupo por `datagramDate`.
7. Calcular deltas validos de distancia y tiempo.
8. Acumular por `lineId`, `year`, `month`.
9. Escribir reporte CSV.

### Comando esperado

```bash
./gradlew :data-center:run --args="monolithic data/datagrams-MiniPilot.csv data/lines-241-ActiveGT.csv results/v1-mini.csv"
```

### Criterio de aceptacion

- Genera un CSV con todas las rutas activas.
- No falla con valores invalidos.
- Los calculos de una muestra manual coinciden con los resultados del programa.
- Sirve como baseline para comparar rendimiento.

## 8. Version 2: solucion concurrente

### Objetivo

Mejorar el tiempo de procesamiento usando varios hilos en una sola maquina.

### Patron recomendado

```text
Master-Worker local / Fork-Join
```

### Estrategia

No se debe partir ingenuamente el archivo por lineas si eso separa datagramas de un mismo viaje. Para mantener la correctitud, particionar por clave:

```text
clave = lineId + busId + tripId
```

Asi todos los datagramas de un mismo viaje quedan en el mismo worker.

### Clases sugeridas

```text
ConcurrentSpeedCalculator.java
Partitioner.java
PartialSpeedResult.java
SpeedResultMerger.java
```

### Flujo

1. Leer rutas activas.
2. Leer datagramas validos.
3. Asignar cada datagrama a una particion usando hash de `lineId`, `busId`, `tripId`.
4. Cada worker procesa su particion.
5. Cada worker devuelve acumulados parciales.
6. El hilo principal combina resultados.
7. Se escribe el mismo formato CSV de la version 1.

### Comando esperado

```bash
./gradlew :data-center:run --args="concurrent data/datagrams-MiniPilot.csv data/lines-241-ActiveGT.csv results/v2-mini.csv 4"
```

El ultimo parametro es el numero de hilos.

### Criterio de aceptacion

- Produce el mismo resultado que la version monolitica.
- Permite ejecutar con diferente numero de hilos.
- Registra tiempo total de ejecucion.
- Muestra mejora en datasets suficientemente grandes.

## 9. Version 3: solucion distribuida

### Objetivo

Distribuir el calculo entre varios procesos o nodos, usando comunicacion remota.

Como el proyecto ya usa Ice, se recomienda implementar la distribucion con Ice.

### Patron recomendado

```text
Master-Worker / MapReduce simplificado
```

### Componentes

```text
Coordinator
  Divide el trabajo y combina resultados.

Worker
  Procesa una particion de datagramas.

Reducer
  Combina acumulados parciales.

Data Center
  Expone o almacena el reporte final.
```

### Contratos Ice sugeridos

Agregar al archivo:

```text
sitm-mio/contracts/src/main/slice/sitm.ice
```

Contratos sugeridos:

```slice
struct SpeedPartial {
    int lineId;
    int year;
    int month;
    double distanceMeters;
    double timeSeconds;
    int samples;
};

sequence<SpeedPartial> SpeedPartialSeq;

interface SpeedWorker {
    SpeedPartialSeq processPartition(string datagramFile, int partitionId, int partitionCount);
};

interface SpeedCoordinator {
    SpeedReportSeq calculateDistributed(string datagramFile, int workers);
};
```

### Flujo distribuido

1. El coordinator recibe archivo de datagramas, archivo de rutas y cantidad de workers.
2. Cada worker procesa solo su particion.
3. La particion se define por hash de `lineId`, `busId`, `tripId`.
4. Cada worker calcula resultados parciales.
5. El coordinator recibe los parciales.
6. El coordinator reduce los resultados.
7. Se genera el CSV final.

### Deployment esperado

Ejemplo en la misma maquina, usando diferentes puertos:

```text
Coordinator: puerto 11000
Worker 1:    puerto 11001
Worker 2:    puerto 11002
Worker 3:    puerto 11003
Worker 4:    puerto 11004
```

En maquinas diferentes:

```text
Coordinator: maquina A
Worker 1: maquina B
Worker 2: maquina C
Worker 3: maquina D
```

### Criterio de aceptacion

- Produce el mismo resultado que la version monolitica.
- Permite variar el numero de workers.
- Documenta el overhead de distribucion.
- Permite justificar desde que tamano vale la pena distribuir.

## 10. Punto a partir del cual vale la pena distribuir

No se debe asumir que distribuir siempre mejora el rendimiento.

Distribuir vale la pena cuando:

```text
tiempo_distribuido < tiempo_concurrente_local
```

y la mejora es significativa:

```text
mejora >= 15% o 20%
```

Tambien vale la pena si:

- El archivo no cabe comodamente en memoria.
- El tiempo local no cumple el objetivo de rendimiento.
- Se quiere escalar hacia millones de datagramas diarios.
- Hay capacidad real de usar varias maquinas.

Conclusion esperada:

- Para `datagrams-MiniPilot.csv`, probablemente no vale la pena distribuir.
- Para `datagrams4Pilot.csv`, la concurrencia local puede ser suficiente.
- Para datasets mucho mas grandes, la distribucion puede superar el overhead de red y coordinacion.

## 11. Experimento de rendimiento

### Datasets

Usar:

```text
datagrams-MiniPilot.csv
datagrams4Pilot.csv
```

Si se necesita encontrar un punto de cruce, crear datasets artificiales concatenando el mini:

```text
mini-x5.csv
mini-x10.csv
mini-x20.csv
mini-x50.csv
```

Debe documentarse que estos datasets son artificiales.

### Metricas

Medir:

- Tiempo total de ejecucion en milisegundos.
- Registros procesados por segundo.
- Numero de hilos o workers.
- Speedup.
- Eficiencia.
- Igualdad de resultados contra la version monolitica.

Formulas:

```text
speedup = tiempo_v1 / tiempo_vN
eficiencia = speedup / numero_de_hilos_o_workers
throughput = registros_procesados / tiempo_segundos
```

### Tabla de resultados sugerida

```text
Dataset       Version       Hilos/Workers   Tiempo ms   Throughput   Speedup   Resultado correcto
MiniPilot     Monolitica    1               TBD         TBD          1.00      Si
MiniPilot     Concurrente   2               TBD         TBD          TBD       Si
MiniPilot     Concurrente   4               TBD         TBD          TBD       Si
MiniPilot     Distribuida   2               TBD         TBD          TBD       Si
Pilot4        Monolitica    1               TBD         TBD          1.00      Si
Pilot4        Concurrente   4               TBD         TBD          TBD       Si
Pilot4        Distribuida   4               TBD         TBD          TBD       Si
```

## 12. Drivers de arquitectura QAW

Crear o completar:

```text
sitm-mio/docs/QAW_SCENARIOS.md
```

Escenarios sugeridos:

### QA-01 Rendimiento

Fuente: Analista de datos.

Estimulo: Se ejecuta el calculo mensual sobre `datagrams4Pilot.csv`.

Ambiente: Sistema en ejecucion normal.

Artefacto: Modulo `data-center`.

Respuesta: El sistema calcula velocidades para todas las rutas activas.

Medida: El tiempo de ejecucion debe ser menor que la version monolitica al usar concurrencia.

### QA-02 Correctitud

Fuente: Profesor o evaluador.

Estimulo: Se compara el resultado del sistema contra una muestra calculada manualmente.

Ambiente: Dataset piloto.

Artefacto: Calculador de velocidad.

Respuesta: El sistema reporta la misma velocidad promedio para la muestra.

Medida: Diferencia maxima aceptada menor a 0.01 km/h.

### QA-03 Escalabilidad

Fuente: Operador del sistema.

Estimulo: El volumen de datagramas aumenta de 1x a 9x o mas.

Ambiente: Ejecucion batch historica.

Artefacto: Calculador concurrente y distribuido.

Respuesta: El sistema aumenta throughput al agregar hilos o workers.

Medida: El throughput mejora al menos 15% en datasets grandes.

### QA-04 Modificabilidad

Fuente: Equipo de desarrollo.

Estimulo: Se agrega una nueva estrategia de calculo.

Ambiente: Desarrollo.

Artefacto: Capa de analisis.

Respuesta: Se agrega la estrategia sin cambiar lectores CSV ni formato de salida.

Medida: Maximo 2 clases existentes modificadas.

### QA-05 Disponibilidad ante fallos de worker

Fuente: Coordinator distribuido.

Estimulo: Un worker remoto no responde.

Ambiente: Ejecucion distribuida.

Artefacto: Coordinator.

Respuesta: El coordinator registra el fallo y reporta error o reintenta la particion.

Medida: El fallo no produce resultados silenciosamente incorrectos.

### QA-06 Observabilidad

Fuente: Equipo de pruebas.

Estimulo: Se ejecuta cualquier version del calculo.

Ambiente: Pruebas de rendimiento.

Artefacto: CLI de analisis.

Respuesta: El sistema imprime dataset, version, tiempo, registros procesados y archivo de salida.

Medida: Toda ejecucion queda trazable.

## 13. Deployment y patrones

Crear:

```text
sitm-mio/docs/DEPLOYMENT_AND_PATTERNS.md
```

Contenido minimo:

### Version 1

```text
[CSV Routes] + [CSV Datagrams] -> [Monolithic Calculator] -> [CSV Report]
```

Patrones:

- Batch Processing.
- Repository para lectura de datos.
- Strategy para intercambiar algoritmos.

### Version 2

```text
[CSV Datagrams] -> [Local Coordinator]
                     -> [Worker Thread 1]
                     -> [Worker Thread 2]
                     -> [Worker Thread N]
                  -> [Merger] -> [CSV Report]
```

Patrones:

- Master-Worker local.
- Fork-Join.
- Strategy.

### Version 3

```text
[Coordinator Node]
      |
      | Ice RPC
      v
[Worker Node 1] [Worker Node 2] [Worker Node N]
      |
      v
[Partial Results]
      |
      v
[Reducer]
      |
      v
[Final Report]
```

Patrones:

- Master-Worker.
- MapReduce simplificado.
- RPC.
- DTO.

## 14. Documento de resultados

Crear:

```text
sitm-mio/docs/EXPERIMENT_RESULTS.md
```

Estructura:

```text
1. Objetivo
2. Ambiente de ejecucion
3. Datasets usados
4. Formula de calculo
5. Limpieza de datos
6. Validacion de correctitud
7. Resultados de rendimiento
8. Punto donde vale la pena distribuir
9. Conclusiones
10. Limitaciones
```

Ambiente de ejecucion:

```text
Sistema operativo:
CPU:
RAM:
Java:
Gradle:
Fecha:
```

## 15. Orden recomendado de implementacion

Checklist principal:

```text
[ ] Entrar por SSH al servidor y confirmar que existen los datos en /opt/sitm-mio
[ ] Revisar tamanos y numero de filas con ls -lh y wc -l
[ ] Copiar datagrams-MiniPilot.csv a sitm-mio/data/
[ ] Copiar datagrams4Pilot.csv a sitm-mio/data/
[ ] Verificar que los archivos copiados aparecen en sitm-mio/data/
[ ] Implementar modelos de datos
[ ] Implementar lector de rutas activas
[ ] Implementar lector de datagramas
[ ] Implementar acumulador de velocidad
[ ] Implementar version monolitica
[ ] Generar results/v1-mini.csv
[ ] Validar manualmente una muestra
[ ] Implementar version concurrente
[ ] Comparar v1-mini.csv vs v2-mini.csv
[ ] Ejecutar pruebas de rendimiento con datagrams4Pilot.csv
[ ] Agregar contratos Ice para workers distribuidos
[ ] Implementar worker distribuido
[ ] Implementar coordinator distribuido
[ ] Ejecutar experimentos
[ ] Crear QAW_SCENARIOS.md
[ ] Crear DEPLOYMENT_AND_PATTERNS.md
[ ] Crear EXPERIMENT_RESULTS.md
```

## 16. Riesgos y decisiones importantes

### Riesgo 1: archivos oficiales faltantes

Impacto:

```text
No se puede validar la entrega completa con los datasets requeridos.
```

Mitigacion:

```text
Usar `chunck.csv` solo para pruebas iniciales. Copiar los archivos oficiales desde `swarch@206m03:/opt/sitm-mio` antes de los experimentos finales.
```

### Riesgo 2: calculo incorrecto por particionamiento

Impacto:

```text
Si se divide por lineas del archivo, un mismo viaje puede quedar en dos workers y se pierden deltas.
```

Mitigacion:

```text
Particionar por hash(lineId, busId, tripId).
```

### Riesgo 3: overhead de distribucion

Impacto:

```text
La version distribuida puede ser mas lenta en datasets pequenos.
```

Mitigacion:

```text
Medir y documentar el punto de cruce.
```

### Riesgo 4: datos invalidos

Impacto:

```text
Odometer negativo, fechas invalidas o deltas negativos pueden alterar el promedio.
```

Mitigacion:

```text
Implementar filtros de limpieza y reportar cuantos registros se descartaron.
```

## 17. Criterio final de entrega

La entrega se considera completa si:

- Existe una version monolitica correcta.
- Existe una version concurrente que produce los mismos resultados.
- Existe una version distribuida basada en Master-Worker o MapReduce simplificado.
- Hay resultados experimentales comparando las tres versiones.
- Se justifica desde que tamano vale la pena distribuir.
- Los drivers QAW estan documentados.
- El deployment y los patrones estan documentados.
- La implementacion puede ejecutarse con comandos claros.
