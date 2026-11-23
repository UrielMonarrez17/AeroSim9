# Avance: clientes y policias

Basado en el reporte y el BPMN proporcionados, con la correccion del profesor:
no existe una clase Terrorist. Cada `Client` sortea una sola vez su atributo
`terrorist` con probabilidad 0.05. No significa exactamente cinco por cada cien.

## Ejecutar (JDK 21)

```powershell
javac -encoding UTF-8 -d build Client.java Police.java SecurityArea.java SecurityDemo.java SecurityTest.java
java -cp build SecurityDemo
java -cp build SecurityTest
```

La demo crea 40 clientes, dos policias y una sala con capacidad de diez.
El sorteo de clientes usa semilla 9; el orden de los hilos y las revisiones varian.
La interfaz original todavia referencia una clase `Agent` inexistente, por lo que
este avance se compila de forma aislada; `Main` sigue pendiente de integracion.

## Flujo implementado

- Cliente: ARRIVING -> WAITING -> READY_TO_BOARD.
- Si lo revisan: WAITING -> BEING_QUESTIONED -> WAITING (inocente),
  o ARRESTED -> PROCESSED (terrorista).
- Un terrorista no seleccionado para revision puede quedar listo para abordar.
- Policia: PATROLLING -> INVESTIGATING -> PATROLLING; descansa cada cierto
  numero de rondas y termina en STOPPED cuando se interrumpe su hilo.
- CANCELLED representa la interrupcion de un cliente.

`SecurityArea` coordina con `ReentrantLock` y `Condition`. La sala de espera
bloquea nuevas entradas al llenarse. Se reserva el cupo del cliente durante su
revision para que el inocente pueda regresar sin bloquear al policia. Otro
cerrojo asegura una sola pareja policia-cliente durante todo el interrogatorio.
La decision de salir de espera y la seleccion policial usan el mismo cerrojo.
Las interrupciones liberan recursos y despiertan a los hilos en espera.

Supuestos configurables, no cifras del reporte: 30% de probabilidad de intentar
una revision por ronda policial, patrulla de 100 ms, investigacion de 150 ms,
descanso de 300 ms cada cinco rondas. La demo espera 1200 ms por cliente.
El 30% no es una probabilidad por cliente: depende del tiempo en sala y las rondas.
En esta primera version, una revision terminada detecta siempre al terrorista;
no se modelan falsos positivos o negativos. Un inocente revisado no se revisa otra vez.

## Siguiente integracion

READY_TO_BOARD es el limite de este avance; no significa que el pasajero ya
este dentro de un avion. Por ahora el tiempo de espera sustituye al llamado
del avion. Falta conectar la salida de sala con el consumidor avion, la pasarela
exclusiva, los 20 asientos y los estados de vuelo/desembarque. No se implementan
la torre, sockets ni representacion grafica en este modulo.

En el BPMN, fusionar ambos carriles de pasajeros en Cliente, anotar el atributo
`terrorist (5%)` en la creacion y agregar desde el interrogatorio la bifurcacion
inocente/liberado o terrorista/arrestado/procesado. Los archivos originales de
Downloads se conservan sin cambios.
