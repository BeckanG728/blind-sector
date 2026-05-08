# Blind Sector — Diseño Técnico Base

> Proyecto de aprendizaje táctico multijugador. Entorno controlado para validar resolución simultánea, snapshots de estado y mecánicas de información parcial, como base hacia proyectos más complejos.

---

## 1. Visión General

**Blind Sector** es un juego táctico por turnos 1v1 donde dos jugadores se posicionan en un tablero y se bombardean mutuamente sin conocer la posición exacta del rival. La incertidumbre espacial es el núcleo táctico: solo se revela la *región general* donde se encuentra el oponente al final de cada turno.

### Principios de diseño

- **Información parcial**: nunca se expone la posición exacta del rival.
- **Resolución simultánea**: ambos jugadores actúan al mismo tiempo, sin alternancia.
- **Alcance controlado**: sin mecánicas persistentes complejas (minas, cadenas, alteraciones del mapa).
- **Arquitectura modular**: cada sistema es independiente y reutilizable.

---

## 2. Configuración del Tablero

### Tamaño

El tablero del MVP es fijo:

```
15 × 15 = 225 celdas
```

### Segmentación por regiones

El tablero se divide en una cuadrícula de **3×3 regiones**, cada una de **5×5 celdas**:

```
┌─────┬─────┬─────┐
│ A1  │ A2  │ A3  │
├─────┼─────┼─────┤
│ B1  │ B2  │ B3  │
├─────┼─────┼─────┤
│ C1  │ C2  │ C3  │
└─────┴─────┴─────┘
```

Cada región contiene exactamente **25 celdas** (5 columnas × 5 filas).

### Coordenadas

Las celdas se identifican como `(col, row)` con índices `0–14`. Las regiones se determinan automáticamente:

```
región_col = floor(col / 5)   →  0 = A, 1 = B, 2 = C
región_row = floor(row / 5)   →  0 = 1, 1 = 2, 2 = 3
```

### Nota de balance — densidad informacional

Cada región contiene 25 celdas y el bombardeo cubre 9 celdas (área 3×3). Un solo disparo dirigido al centro de la región detectada cubre el **36% de esa región**. En pocas rondas el espacio de búsqueda colapsa significativamente.

Esto no es un defecto para el MVP, pero implica que las partidas serán cortas y el factor táctico dominante es el reposicionamiento, no la ocultación prolongada. Si en iteraciones futuras se quiere aumentar la incertidumbre, las opciones son: ampliar el tablero, reducir el área de bombardeo, o revelar solo una subregión en lugar de la región completa.

---

## 3. Mecánica de Ataque — Bombardeo 3×3

### Selección

Cada turno el jugador declara un punto de impacto central:

```
(x, y)
```

### Área de efecto

El ataque impacta la celda central más las 8 adyacentes, formando un área de **3×3 = 9 celdas**:

```
[ ][ ][ ]
[ ][X][ ]
[ ][ ][ ]
```

Las celdas que quedan fuera del tablero se ignoran (no producen error, simplemente no impactan).

### Validaciones del servidor

Antes de aceptar un ataque, el servidor verifica:

- El punto central `(x, y)` está dentro de los límites del tablero (`0 ≤ x,y ≤ 14`).
- El turno declarado en la acción coincide con el turno activo de la partida.
- El jugador no ha enviado una acción en este turno todavía (deduplicación por `pendingActions`).

---

## 4. Movimiento y Sistema de Información Parcial

### Movimiento por turno

Cada jugador puede desplazarse hasta **4 celdas por turno**. La métrica de distancia utilizada es **Chebyshev**:

```
distancia = max(|dx|, |dy|) ≤ 4
```

Esto permite movimiento libre en las 8 direcciones (horizontal, vertical y diagonal) de forma consistente. Un paso diagonal cuenta igual que uno recto, equivalente al movimiento del rey en ajedrez extendido a 4 pasos.

Las otras métricas fueron descartadas: Manhattan restringe desproporcionadamente las diagonales; Euclidiana introduce distancias inconsistentes en grids discretos (`sqrt(2)` no es entero).

**Validación en el servidor:**

```java
int dx = Math.abs(action.moveToX - currentX);
int dy = Math.abs(action.moveToY - currentY);
if (Math.max(dx, dy) > 4) {
    return error("Movimiento inválido: excede distancia Chebyshev 4");
}
if (action.moveToX < 0 || action.moveToX > 14 ||
    action.moveToY < 0 || action.moveToY > 14) {
    return error("Movimiento inválido: fuera de tablero");
}
```

### Información revelada al rival

Al finalizar la resolución de un turno, el servidor **no revela la posición exacta** del oponente. Solo se comunica la **región actual** del rival tras aplicar su movimiento:

```json
{ "enemy_region": "B2" }
```

### Objetivo táctico

La incertidumbre obliga a los jugadores a predecir hacia dónde se mueve el rival, decidir si bombardear el centro o los bordes de la región detectada, y evaluar si moverse expone información valiosa.

---

## 5. Sistema de Daño y Bono Sniper

### Daño estándar

Si el jugador **se movió** durante el turno y su bombardeo impacta al rival:

```
25 HP de daño
```

### Bono Sniper

Si el jugador **no se movió** (permaneció en la misma celda) y su bombardeo impacta:

```
35 HP de daño
```

El bono representa mayor tiempo de apuntado y precisión al no cambiar de posición.

### HP inicial

Cada jugador comienza con **100 HP**.

### Condición de victoria

Un jugador gana cuando el HP del rival llega a **0 o menos** tras la resolución del turno. Es posible la **eliminación mutua** si ambos ataques impactan en el mismo turno.

### Nota de balance — bono Sniper

Con tablero de 15×15 y regiones de 25 celdas, el incentivo a quedarse quieto puede ser contraproducente: un jugador inmóvil es predecible. El diferencial de 10 HP entre 25 y 35 puede ajustarse según playtest. Si la movilidad resulta demasiado penalizada tácticamente, reducir el diferencial a 5–8 HP o condicionar el bono a no haberse movido en dos turnos consecutivos son alternativas a evaluar.

---

## 6. Estados de la Partida

Una partida atraviesa los siguientes estados de forma estrictamente secuencial:

```
WAITING → ACTIVE → RESOLVING → ACTIVE → ... → FINISHED
```

| Estado | Descripción |
|---|---|
| `WAITING` | Partida creada, esperando que ambos jugadores se conecten |
| `ACTIVE` | Turno abierto, el servidor acepta acciones de ambos jugadores |
| `RESOLVING` | Ambas acciones recibidas, el servidor está ejecutando la resolución del turno |
| `FINISHED` | La partida terminó (victoria, derrota o empate) |

El estado `RESOLVING` es transitorio y de duración muy corta (microsegundos). Su función es bloquear nuevas acciones durante la resolución, evitando condiciones de carrera fuera del lock. Ningún cliente debería ver este estado durante uso normal; si lo ve, significa que debe reintentar el polling.

**Transiciones inválidas rechazadas por el servidor:**

- Enviar acción cuando el estado no es `ACTIVE`.
- Iniciar resolución si el estado ya es `RESOLVING` o `FINISHED`.

---

## 7. Resolución Simultánea

Ambos jugadores envían sus acciones de forma independiente. El turno se resuelve únicamente cuando **ambos han enviado** su acción dentro del lock de la partida.

### Orden de resolución

```
1. Ambos jugadores envían: movimiento + punto de ataque
2. Se aplica el movimiento de ambos jugadores
3. Se verifica si algún jugador está en el área de impacto del rival
4. Se calcula y aplica el daño (incluyendo bono Sniper si corresponde)
5. Se verifica si algún jugador llegó a 0 HP
6. Se genera el snapshot del turno
7. Se envía el snapshot a ambos clientes
```

Este orden garantiza resolución **determinista**: el mismo estado de entrada siempre produce el mismo resultado.

---

## 8. Snapshot de Turno

Al finalizar cada resolución, el servidor emite un snapshot que se entrega a ambos jugadores.

### Estructura del snapshot

```json
{
  "turn": 4,
  "result": {
    "hit": "HIT",
    "damage_dealt": 35,
    "damage_received": 0
  },
  "impact_area": {
    "center": { "x": 7, "y": 9 },
    "cells": [[6,8],[7,8],[8,8],[6,9],[7,9],[8,9],[6,10],[7,10],[8,10]]
  },
  "enemy_region": "C1",
  "self": {
    "hp": 75,
    "position": { "x": 7, "y": 3 },
    "moved": false
  },
  "game_status": "active",
  "winner": null
}
```

### Valores posibles de `hit`

| Valor | Descripción |
|---|---|
| `MISS` | El ataque no impactó al rival |
| `HIT` | El ataque impactó (daño estándar o bono Sniper) |
| `DIRECT HIT` | El rival estaba en la celda central exacta |

### Notas de implementación

- `impact_area.cells` incluye todas las celdas del área 3×3 válidas dentro del tablero. Usadas por el frontend para animaciones.
- `self.position` solo se incluye en el snapshot propio, nunca en el del rival.
- `game_status` refleja el estado de la partida tras la resolución: `active`, `finished`.

---

## 9. Modelo de Datos y Estrategia de Persistencia

### Jerarquía de autoridad

Cada capa tiene un rol estricto y no se solapa con las demás:

| Capa | Tecnología | Rol | Cuándo se accede |
|---|---|---|---|
| **Memory** | `GameState` en memoria (Java) | Autoridad única del estado operativo | En cada acción de turno |
| **Checkpoint** | Archivo JSON en disco | Recuperación ante fallo (post-MVP) | Por evento crítico o tiempo |
| **MySQL** | Tabla `games` | Metadata no operativa de la partida | Al crear y al finalizar únicamente |

La memoria es la única fuente de verdad durante una partida activa. MySQL no se consulta para resolver turnos. El checkpoint no se lee durante la partida, solo al reiniciar el servidor.

---

### Concurrencia — locking explícito por partida

`ConcurrentHashMap` garantiza seguridad sobre el mapa de partidas, pero no sobre la lógica de resolución. El problema concreto sin lock:

```
Thread A (jugador A):  lee pendingActions → size = 1
Thread B (jugador B):  lee pendingActions → size = 1
Thread A: inserta acción → size = 2 → intenta resolver
Thread B: inserta acción → size = 2 → también intenta resolver
→ doble resolución del mismo turno
```

La solución es un **`ReentrantLock` por partida**. Solo un thread ejecuta la lógica de resolución a la vez por partida, sin bloquear otras partidas simultáneas.

```java
class GameState {
    String gameId;
    GameStatus status;  // WAITING, ACTIVE, RESOLVING, FINISHED
    int turn;
    PlayerState playerA;
    PlayerState playerB;
    Map<String, TurnAction> pendingActions = new HashMap<>();
    final ReentrantLock lock = new ReentrantLock();
}
```

Flujo de recepción de acción:

```java
GameState game = activeGames.get(gameId);

game.lock.lock();
try {
    if (game.status != GameStatus.ACTIVE) {
        return error("Partida no está en estado ACTIVE");
    }
    if (action.turn != game.turn) {
        return error("Acción extemporánea: turno incorrecto");
    }
    if (game.pendingActions.containsKey(playerId)) {
        return error("Acción duplicada: ya enviaste este turno");
    }

    game.pendingActions.put(playerId, action);

    if (game.pendingActions.size() == 2) {
        game.status = GameStatus.RESOLVING;
        resolveTurn(game);   // ejecuta dentro del lock
        game.status = GameStatus.ACTIVE;
    }
} finally {
    game.lock.unlock();
}
```

Dentro del lock, `size() == 2` es completamente seguro. El estado `RESOLVING` bloquea cualquier acción adicional que llegue durante la resolución.

---

### Acciones versionadas e idempotencia HTTP

`TurnAction` incluye el número de turno declarado por el cliente. Esto permite detectar y rechazar acciones extemporáneas por reintentos de red, sin necesidad de request IDs externos.

```java
class TurnAction {
    String playerId;
    int turn;           // turno declarado por el cliente al enviar
    int moveToX;
    int moveToY;
    int attackX;
    int attackY;
    long submittedAt;   // timestamp de recepción en el servidor
}
```

**Deduplicación garantizada por dos capas:**

1. `action.turn != game.turn` — rechaza acciones de turnos anteriores o futuros.
2. `pendingActions.containsKey(playerId)` — rechaza un segundo envío del mismo jugador en el turno actual.

Ambas validaciones ocurren dentro del lock, por lo que son seguras bajo concurrencia. Un cliente que reintente la misma petición por lag recibirá un error controlado, no una resolución duplicada.

---

### Timeouts de turno

Sin timeout, una partida queda colgada indefinidamente si un jugador se desconecta o nunca envía su acción. El servidor debe cancelar el turno si transcurre demasiado tiempo sin que ambos jugadores actúen.

**Estrategia para el MVP:**

Un hilo de monitoreo (o un scheduler) revisa periódicamente las partidas en estado `ACTIVE` con solo una acción recibida. Si el tiempo desde la primera acción supera el límite configurado, el servidor:

1. Asigna una acción por defecto al jugador ausente (sin movimiento, ataque fuera de tablero inválido que resulta en `MISS`), o bien
2. Declara al jugador ausente como perdedor por abandono.

El comportamiento exacto ante timeout (penalización vs. abandono) se define en la fase de implementación. Lo importante es que el mecanismo exista para que las partidas no queden en estado `ACTIVE` permanentemente.

```java
// Ejemplo de campo de control en GameState
long firstActionReceivedAt;   // timestamp cuando llegó la primera acción del turno
// El scheduler verifica: si (now - firstActionReceivedAt) > TURN_TIMEOUT_MS → resolver forzado
```

---

### Esquema MySQL (metadata no operativa)

Una sola tabla. No se lee ni escribe durante los turnos intermedios.

**`games`** — una fila por partida:

| Campo | Tipo | Descripción |
|---|---|---|
| `game_id` | VARCHAR PK | Identificador único |
| `player_a_id` | VARCHAR | ID del jugador A |
| `player_b_id` | VARCHAR | ID del jugador B |
| `status` | ENUM | `waiting`, `active`, `finished` |
| `winner_id` | VARCHAR | ID del ganador (`null` hasta terminar, `"draw"` si empate) |
| `turns_played` | INT | Total de turnos al finalizar |
| `created_at` | DATETIME | Timestamp de creación |

MySQL recibe exactamente **2 escrituras por partida**: `INSERT` al crearla, `UPDATE` al terminarla.

Si un cliente se reconecta en medio de una partida, el servidor responde con el `GameState` actual desde memoria. MySQL no contiene el estado operativo necesario para reconstruir una partida activa.

---

### Ciclo de resolución por turno

```
Jugador envía acción  (HTTP POST)
    │
    ▼
game.lock.lock()
    │
    ├── status != ACTIVE?          → rechazar
    ├── action.turn != game.turn?  → rechazar (extemporánea)
    ├── playerId ya en pending?    → rechazar (duplicada)
    │
    ▼
pendingActions.put(playerId, action)
    │
    ▼
¿pendingActions.size() == 2?
    │
    ├── NO ──→ registrar firstActionReceivedAt si es la primera
    │          game.lock.unlock()
    │          responder "acción recibida"
    │
    └── SÍ ──→ game.status = RESOLVING
               resolveTurn(game)
                   ├── aplicar movimientos (validar Chebyshev)
                   ├── calcular impactos y daño
                   ├── actualizar hp, posiciones, turno++
                   └── limpiar pendingActions
               game.status = ACTIVE  (o FINISHED si terminó)
               game.lock.unlock()
                   │
                   ▼
              ¿partida terminada?
                   ├── NO  → fin, estado actualizado en memoria
                   └── SÍ  → UPDATE games SET status, winner_id  ← MySQL
                             eliminar GameState de activeGames
```

---

### Checkpoints JSON (post-MVP, implementación opcional)

Para el MVP (1v1, turnos lentos, baja escala) los checkpoints son sobreingeniería. Se documentan aquí como extensión futura.

Si se implementan, el criterio de disparo debe basarse en eventos o tiempo, **no en número fijo de turnos**:

| Criterio | Ejemplo |
|---|---|
| **Evento crítico** | Algún jugador queda por debajo de 30 HP tras resolución |
| **Delta significativo** | El HP total bajó más de 50 puntos en el turno |
| **Por tiempo** | Han pasado más de 60 segundos desde el último checkpoint |

Un número fijo de turnos (`turno % N`) no refleja la importancia real del estado — puede haber turnos sin impacto que no merecen checkpoint y turnos decisivos que sí.

---

## 10. Arquitectura del Sistema

### Componentes principales

```
Cliente (Browser)
    │
    │  HTTP polling
    ▼
Aplicación Java
    ├── Validación de acción (status, turno, duplicado)
    ├── Lock por partida (ReentrantLock)
    ├── Motor de resolución simultánea
    ├── Scheduler de timeouts de turno
    ├── Generador de snapshots
    └── Respuesta al cliente
         │
         ├──────────────────────┬──────────────────────────┐
         ▼                      ▼                          ▼
  ConcurrentHashMap        Archivos JSON               MySQL
  (activeGames)            (checkpoints/ — post-MVP)   (tabla games)
  Autoridad única          Recuperación ante fallo      Metadata no operativa
  del estado activo        por evento o tiempo          Al crear y al terminar
```

### Estrategia de polling

Para el MVP se recomienda **polling periódico** desde el cliente (cada 1–2 segundos) para verificar si el turno fue resuelto. El endpoint devuelve el snapshot si el turno ya se resolvió, o un indicador de espera si todavía no. WebSockets pueden incorporarse en iteraciones posteriores.

### Frontend

El frontend se construye con **CSS Grid** (en lugar de Canvas) para menor complejidad de renderizado. Responsabilidades del frontend:

- Generar el grid 15×15 con separación visual de regiones.
- Permitir seleccionar celda de movimiento y celda de ataque.
- Mostrar la región detectada del enemigo al recibir el snapshot.
- Animar el área 3×3 de impacto con las celdas recibidas en `impact_area.cells`.

---

## 11. Flujo de Turno — Diagrama

```
Jugador A                    Servidor                    Jugador B
    │                           │                            │
    │── POST acción ───────────>│                            │
    │   { turn, move, attack }  │<──── POST acción ──────────│
    │                           │      { turn, move, attack }│
    │                  [ lock → RESOLVING ]                  │
    │                  [ resolveTurn()    ]                  │
    │                  [ ACTIVE / FINISHED]                  │
    │                           │                            │
    │<─── snapshot propio ──────│                            │
    │                           │──── snapshot propio ──────>│
    │                           │                            │
    │── GET /state (polling) ──>│                            │
    │<── snapshot (si resuelto) │                            │
```

---

## 12. Alcance del MVP

### Incluido

- Tablero 15×15 con segmentación en 9 regiones.
- Movimiento Chebyshev ≤ 4 con validación en servidor.
- Ataque de área 3×3 con validación de bordes.
- Estados de partida: `WAITING`, `ACTIVE`, `RESOLVING`, `FINISHED`.
- Resolución simultánea con `ReentrantLock` por partida.
- Deduplicación de acciones por turno versionado + `containsKey`.
- Sistema de daño estándar (25 HP) y bono Sniper (35 HP).
- Revelación de región del enemigo al final del turno.
- Snapshot de turno con datos suficientes para animaciones.
- Detección de victoria, derrota y empate.
- Timeout de turno básico (scheduler de abandono).
- MySQL para metadata: `INSERT` al crear, `UPDATE` al terminar.

### Fuera del alcance (MVP)

- Checkpoints JSON de recuperación.
- Salas públicas y matchmaking automático.
- Autenticación de usuarios.
- Historial de partidas persistente.
- Efectos de terreno o alteraciones del mapa.
- Chat en partida.
- WebSockets (se usa polling).

---

## 13. Objetivo Arquitectónico

Blind Sector está diseñado como entorno de aprendizaje y validación técnica para los siguientes sistemas:

| Sistema | Validación |
|---|---|
| Resolución simultánea determinista | Motor de turno con lock y snapshot |
| Concurrencia segura por partida | `ReentrantLock` + estado `RESOLVING` |
| Información parcial | Región visible vs. posición oculta |
| Idempotencia HTTP | Turno versionado + deduplicación por `containsKey` |
| Polling multijugador | Sincronización cliente-servidor sin WebSocket |
| Separación Memory / Persistencia | Estado efímero en memoria, metadata en MySQL |
| Timeouts y desconexión | Scheduler de turno con resolución forzada |

Los sistemas validados aquí son directamente reutilizables en proyectos más complejos como **Shadow Grid**.
