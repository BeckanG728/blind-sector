# Blind Sector — Estructura del Proyecto

> Monolito modular organizado por dominio funcional, no por capas genéricas globales.
> La separación por dominio evita que el proyecto se convierta en un "god project" difícil de mantener.

---

## Estructura raíz

```
src/main/java/com/blindsector
│
├── BlindSectorApplication.java
│
├── shared/
├── config/
├── infrastructure/
├── contracts
│
├── game/
├── player/
├── lobby/
├── turn/
├── snapshot/
└── persistence/
```

---

## 1. `shared/`

Componentes reutilizables y neutrales al dominio.

```
shared/
├── exception/
├── constants/
├── utils/
├── dto/
└── enums/
```

**Responsabilidad:** excepciones globales, enums reutilizables, DTOs compartidos, helpers matemáticos y validaciones
genéricas.

**Ejemplos:**

- `shared/enums/GameStatus.java` — estados `WAITING`, `ACTIVE`, `RESOLVING`, `FINISHED`
- `shared/enums/HitResult.java` — `MISS`, `HIT`, `DIRECT_HIT`
- `shared/utils/GridUtils.java` — cálculo Chebyshev, resolución de región, validación de límites
- `shared/exception/GameException.java`

---

## 2. `config/`

Configuración técnica de Spring Boot.

```
config/
├── JacksonConfig.java
├── AsyncConfig.java
├── SchedulerConfig.java
├── CorsConfig.java
└── WebConfig.java
```

**Responsabilidad:** beans, scheduler, configuración JSON, CORS, serialización, thread pools.

---

## 3. `infrastructure/`

Infraestructura técnica transversal. Define cómo el sistema gestiona estado en memoria, concurrencia y timeouts.

```
infrastructure/
├── memory/
│   ├── ActiveGamesRegistry.java
│   └── GameMemoryStore.java
│
├── lock/
│   ├── GameLockManager.java
│   └── LockExecutor.java
│
└── scheduler/
    └── TurnTimeoutScheduler.java
```

### `memory/`

Centraliza el estado runtime de todas las partidas activas.

```java
// ActiveGamesRegistry encapsula este mapa — no se dispersa por el proyecto
ConcurrentHashMap<String, GameState> activeGames
```

**Responsabilidad de `ActiveGamesRegistry`:** exclusivamente CRUD de `GameState` activos. Sin lógica de juego, sin
resolución, sin timeouts.

### `lock/`

Encapsula el locking explícito por partida. Aunque cada `GameState` tiene su propio `ReentrantLock`, centralizar la
lógica de adquisición/liberación en `GameLockManager` evita duplicación, deadlocks accidentales y manejo inconsistente
entre servicios.

### `scheduler/`

Monitoreo periódico de timeouts de turno.

**Responsabilidad de `TurnTimeoutScheduler`:** escanear partidas activas, detectar turnos que superaron el tiempo límite
leyendo `firstActionReceivedAt` desde `ActiveGamesRegistry`, y **delegar** la resolución forzada. El scheduler nunca
modifica `GameState` directamente.

```java
// Correcto
turnTimeoutService.forceResolveTimeout(gameId);

// Incorrecto — rompe encapsulación y bypasea el lock
game.pendingActions.

put(...);

resolveTurn(game);
```

**Dirección de dependencia — sentido único, nunca al revés:**

```
TurnTimeoutScheduler
    ↓
ActiveGamesRegistry  (solo lectura de timestamps y estado)
    ↓
TurnTimeoutService
    ↓
TurnCoordinator / GameService
    ↓
Lock → resolveTurn()
```

---

## 4. `game/`

Dominio principal del juego.

```
game/
├── controller/
├── service/
├── domain/
├── engine/
├── validation/
└── dto/
```

### `domain/`

Estado puro del juego. **Sin dependencias Spring.**

```
domain/
├── GameState.java
├── Board.java
├── Position.java
├── Region.java
└── ImpactArea.java
```

### `engine/`

Motor determinista de resolución. Módulo crítico.

```
engine/
├── TurnResolver.java
├── DamageCalculator.java
├── MovementEngine.java
├── RegionResolver.java
└── ImpactResolver.java
```

Aquí vive: resolución simultánea, cálculo de daño (estándar 25 HP / bono Sniper 35 HP), validación de impacto,
generación de resultados y validaciones espaciales (Chebyshev, límites de tablero).

**Este módulo debe ser testeable sin levantar Spring.**

### `validation/`

```
validation/
├── MovementValidator.java   — Chebyshev ≤ 4, límites del tablero
├── AttackValidator.java     — punto central dentro del tablero
└── TurnValidator.java       — turno versionado, duplicados, estado ACTIVE
```

Separar validaciones evita acumular cientos de líneas de lógica condicional en `GameService`.

### `service/`

Orquestación entre engine, memoria y locks. **Sin lógica matemática.**

```
service/
├── GameService.java
├── TurnSubmissionService.java
└── GameLifecycleService.java
```

### `controller/`

Controladores delgados. Controller ≠ lógica.

```
controller/
├── GameController.java
└── TurnController.java
```

---

## 5. `turn/`

Dominio de acciones y coordinación de turno, separado de `game/` para mayor claridad.

```
turn/
├── domain/
│   ├── TurnAction.java           — incluye turn (versión), playerId, move, attack, submittedAt
│   ├── PendingTurn.java
│   └── TurnResolutionResult.java
│
├── dto/
│
└── service/
    ├── TurnCoordinator.java
    └── TurnTimeoutService.java
```

**Nota importante:** `turn/` no tiene `repository/`. Las acciones de turno (`TurnAction`) viven exclusivamente en
memoria dentro de `GameState.pendingActions`. No se persisten en MySQL. Añadir un repositorio aquí implicaría
persistencia de acciones, lo que contradice directamente la premisa de que Memory es la única autoridad operativa.

---

## 6. `player/`

```
player/
├── domain/
│   ├── PlayerState.java
│   └── PlayerSession.java
│
├── dto/
└── service/
```

Separado desde el inicio para evitar mezclar estado de jugador con lógica de partida a medida que el proyecto crece.

---

## 7. `snapshot/`

Conversión de estado interno a respuesta de cliente.

```
snapshot/
├── factory/
│   └── SnapshotFactory.java    — convierte GameState → SnapshotDTO
│
├── dto/
└── service/
```

`SnapshotFactory` es el único punto donde `GameState` se serializa hacia el cliente. Esto evita mezclar serialización
con lógica de juego en otros módulos.

---

## 8. `lobby/`

Gestión del ciclo de vida previo a la partida: crear sala, unirse, iniciar.

```
lobby/
├── controller/
├── service/
└── dto/
```

**Responsabilidad — únicamente:**

- Crear partida (`create game`)
- Unirse a partida por ID (`join game by id`)
- Iniciar partida cuando ambos jugadores están listos (`start game`)

**Fuera del alcance de este módulo:**

- Matchmaking automático
- Colas de búsqueda pública
- Ranking o emparejamiento por nivel

> **Nota de nomenclatura:** el módulo se llama `lobby/` en lugar de `matchmaking/` porque describe con precisión el
> alcance real: un ciclo host/join, no un sistema de emparejamiento. `matchmaking/` implicaría infraestructura de búsqueda
> automática que no existe en el MVP ni está planificada a corto plazo.

---

## 9. `persistence/`

Persistencia aislada del dominio runtime.

```
persistence/
├── entity/
│   └── GameEntity.java
│
├── repository/
│   └── GameRepository.java
│
├── service/
└── mapper/
    └── GameMapper.java
```

`GameMapper` convierte entre `GameEntity` (JPA) y los datos necesarios para crear o cerrar una partida. **Nunca se
mezcla la entidad JPA con el dominio runtime.**

```java
// Incorrecto — destruye el diseño
@Entity
class GameState { ...
}

// Correcto
@Entity
class GameEntity { ...
}          // solo para MySQL

class GameState { ...
}           // dominio runtime, sin anotaciones JPA
```

MySQL recibe exactamente **2 operaciones por partida**: `INSERT` al crear (desde `lobby/`), `UPDATE` al finalizar (desde
`game/`).

---

## 10. `contracts/`

Se definen contratos/interfaces para la comunicacion entre modulos

```
contracts/
├── port/ -> contiene los puertos que se implementaran en cada modulo y ayudaran a pasar datos entre modulos
└── dto/  -> contiene los dto que mapean los datos especificos que se requieren de un modulo
```

## Resumen de dependencias entre módulos

```
lobby/
    └── persistence/   (INSERT al crear partida)

game/
    ├── infrastructure/memory/    (leer/escribir GameState)
    ├── infrastructure/lock/      (adquirir lock por partida)
    ├── turn/                     (coordinar acciones)
    ├── snapshot/                 (generar respuesta)
    └── persistence/              (UPDATE al terminar partida)

turn/
    ├── game/engine/              (delegar resolución)
    └── infrastructure/memory/   (leer GameState)

infrastructure/scheduler/
    ├── infrastructure/memory/   (leer timestamps, solo lectura)
    └── turn/service/            (delegar timeout)

persistence/
    └── (sin dependencias de dominio)

shared/
    └── (sin dependencias — solo es importado)
```
