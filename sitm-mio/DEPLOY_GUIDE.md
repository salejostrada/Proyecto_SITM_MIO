# Guía de Ejecución - Sistema SITM-MIO Distribuido

Este paquete contiene los ejecutables para las versiones Monolítica, Concurrente y Distribuida del análisis de datos de SITM-MIO.

## Requisitos
- Java 17+
- Acceso a los archivos de datos en `/opt/sitm-mio/` (en todos los nodos para la versión distribuida).

## Topología de Red sugerida para la versión Distribuida
- **Nodo Central (Coordinador):** 192.168.131.23
- **Nodo Trabajador 1:** 192.168.131.24
- **Nodo Trabajador 2:** 192.168.131.25

---

## 1. Versión Monolítica
Se ejecuta en una sola máquina y procesa los datos secuencialmente.

```bash
JAVA_OPTS="-Xmx8g" ./bin/data-center monolithic /opt/sitm-mio/datagrams4Pilot.csv /opt/sitm-mio/lines-241-ActiveGT.csv resultados_mono.csv
```

---

## 2. Versión Concurrente
Se ejecuta en una sola máquina aprovechando múltiples hilos.

```bash
# Ejemplo con 4 hilos
JAVA_OPTS="-Xmx8g" ./bin/data-center concurrent /opt/sitm-mio/datagrams4Pilot.csv /opt/sitm-mio/lines-241-ActiveGT.csv resultados_concurrent.csv 4
```

---

## 3. Versión Distribuida

### Paso A: Iniciar Workers (En los nodos .24 y .25)
Ejecutar en cada máquina trabajadora, especificando solo el puerto (ej. 10002). No necesitan acceso al archivo de rutas.

**En 192.168.131.24:**
```bash
JAVA_OPTS="-Xmx8g" ./bin/data-center distributed-worker 10002
```

**En 192.168.131.25:**
```bash
JAVA_OPTS="-Xmx8g" ./bin/data-center distributed-worker 10002
```

### Paso B: Iniciar Coordinador (En el nodo .23)
Ejecutar el análisis indicando las direcciones de los workers.

```bash
JAVA_OPTS="-Xmx8g" ./bin/data-center distributed-coordinator /opt/sitm-mio/datagrams4Pilot.csv /opt/sitm-mio/lines-241-ActiveGT.csv resultados_dist.csv 192.168.131.24:10002 192.168.131.25:10002
```

---

## Estructura del Paquete
- `bin/`: Scripts ejecutables.
- `lib/`: Librerías y dependencias.
- `GUIA.md`: Este manual.
