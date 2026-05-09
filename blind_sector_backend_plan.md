# Blind Sector — Plan de Desarrollo Backend

> Monolito modular Spring Boot · 3 personas · 9 tareas por persona
> El frontend se desarrolla en una fase posterior por los tres en conjunto.

---

## Convenciones

- **[BLOQUEA →]** esta tarea debe completarse antes de que otra persona pueda avanzar.
- **[DEPENDE DE]** la tarea no puede iniciarse hasta que la indicada esté terminada.
- **[INTEGRACIÓN]** punto de encuentro entre personas. Agendar sesión conjunta.
- Los archivos listados en cada tarea son el **mínimo entregable**.

---

## FASE 0 — Definición base

Estas tareas se resuelven en como primera fase
Ninguna persona avanza a su bloque hasta que Fase 0 esté completa y commiteada.

---

### F0-01 · Enums compartidos · [BLOQUEA → F0-02, P1-09, P2-01, P2-05, P3-02, P3-05]

**Directorio:** `shared/enums/`

**Descripción**
Como sistema, necesito enums centralizados que representen los estados posibles de una partida, los resultados de un impacto y los códigos de error de negocio, para que todos los módulos compartan las mismas constantes sin duplicación.

**Criterios de aceptación:**
- `GameStatus` define exactamente: `WAITING`, `ACTIVE`, `RESOLVING`, `FINISHED`
- `HitResult` define exactamente: `MISS`, `HIT`, `DIRECT_HIT`
- `GameErrorCode` es un enum con exactamente estos valores:

| Código           | HTTP | Quién lo lanza                    |
|------------------|------|-----------------------------------|
| GAME_NOT_FOUND   | 404  | LobbyService, GameMemoryStore     |
| INVALID_MOVE     | 400  | MovementEngine, MovementValidator |
| OUT_OF_BOUNDS    | 400  | MovementEngine, MovementValidator |
| INVALID_ATTACK   | 400  | AttackValidator                   |
| STALE_TURN       | 400  | TurnValidator                     |
| DUPLICATE_ACTION | 400  | TurnValidator                     |
| GAME_NOT_ACTIVE  | 409  | TurnValidator, LobbyService       |
| GAME_FULL        | 409  | LobbyService                      |
| SELF_JOIN        | 400  | LobbyService                      |

- Ningún enum equivalente existe en ningún otro paquete
- Ningún módulo hardcodea Strings de error: todos importan `GameErrorCode`
- Sin dependencias de Spring ni de otros módulos

**Archivos mínimos:**
```
shared/enums/GameStatus.java
shared/enums/HitResult.java
shared/enums/GameErrorCode.java
```

---

### F0-02 · Excepción de dominio · [DEPENDE DE F0-01]

**Directorio:** `shared/exception/`

**Descripción**
Como sistema, necesito una excepción de dominio común para que todos los módulos señalen errores de negocio sin usar excepciones genéricas de Java.

**Criterios de aceptación:**
- `GameException` extiende `RuntimeException`
- Tiene campo `errorCode` (GameErrorCode) además del mensaje para distinguir categorías de error en el handler HTTP
- El constructor recibe `GameErrorCode` directamente: `new GameException(GameErrorCode.GAME_NOT_FOUND, "mensaje")`
- Sin dependencias de Spring

**Archivos mínimos:**
```
shared/exception/GameException.java
```

---

### F0-03 · Modelos de posición y región

**Directorio:** `game/domain/`

**Descripción**
Como sistema, necesito clases que representen posiciones en el tablero y regiones geográficas del mismo, para que todos los motores operen sobre estructuras tipadas.

**Criterios de aceptación:**
- `Position` contiene `col` (int) y `row` (int), con `equals`, `hashCode` y `toString`
- `Region` contiene `regionCol` (int) y `regionRow` (int), con `toLabel()` que devuelve strings como `"A1"`, `"B2"`, `"C3"`
- `ImpactArea` contiene `List<Position>` y método `contains(Position)`
- Sin dependencias externas ni de Spring

**Archivos mínimos:**
```
game/domain/Position.java
game/domain/Region.java
game/domain/ImpactArea.java
```

---

### F0-04 · GridUtils — cálculos espaciales del tablero · [BLOQUEA → P2-01, P2-02, P2-05]

**Directorio:** `shared/utils/`

**Descripción**
Como sistema, necesito una clase utilitaria estática que centralice todos los cálculos espaciales del tablero.

**Criterios de aceptación:**
- `chebyshevDistance(int col1, int row1, int col2, int row2)` devuelve `max(|dc|, |dr|)`
- `resolveRegion(int col, int row)` devuelve la `Region` correcta para cualquier celda
- `isInBounds(int col, int row)` devuelve `true` solo para valores en `[0, 14]`
- `computeImpactArea(int centerCol, int centerRow)` devuelve `ImpactArea` con las celdas 3×3 que caen dentro del tablero
- Test cubre: celda en esquina, celda en borde, celda en centro, distancia diagonal, distancia recta, región correcta para los 9 cuadrantes

**Archivos mínimos:**
```
shared/utils/GridUtils.java
shared/utils/GridUtilsTest.java
```

---

### F0-05 · Modelos de dominio runtime — GameState y PlayerState · [BLOQUEA → P1-01, P2-01, P2-03]

**Directorio:** `game/domain/`, `player/domain/`

**Descripción**
Como sistema, necesito las clases que representan el estado completo de una partida activa en memoria.

**Criterios de aceptación:**
- `GameState` contiene: `gameId` (String), `status` (GameStatus), `turnNumber` (int), `playerA` (PlayerState), `playerB` (PlayerState), `pendingActions` (ConcurrentHashMap<String, TurnAction>), `lock` (ReentrantLock), `firstActionReceivedAt` (long)
- `PlayerState` contiene: `playerId` (String), `posCol` (int), `posRow` (int), `hp` (int, inicializado en 100)
- `PlayerSession` contiene: `playerId` y `gameId`
- Sin anotaciones de Spring ni de JPA

**Archivos mínimos:**
```
game/domain/GameState.java
player/domain/PlayerState.java
player/domain/PlayerSession.java
```

---

### F0-06 · Modelos de turno · [BLOQUEA → P2-04, P2-05]

**Directorio:** `turn/domain/`

**Descripción**
Como sistema, necesito las estructuras que representan las acciones enviadas por un jugador y el resultado de resolver un turno.

**Criterios de aceptación:**
- `TurnAction` contiene: `playerId`, `turn` (int), `moveToCol`, `moveToRow`, `attackCol`, `attackRow`, `submittedAt` (long)
- `TurnResolutionResult` contiene: `damageToA`, `damageToB`, `hitResultA`, `hitResultB`, `finalPositionA`, `finalPositionB`, `regionOfBSeenByA`, `regionOfASeenByB`, `impactAreaOfA`, `impactAreaOfB`, `gameOver` (boolean), `winnerId` (String, nullable)
- `TurnCoordinatorResult` contiene: `resolved` (boolean), `waiting` (boolean), `resolutionResult` (nullable)
- Producen JSON válido con Jackson sin configuración adicional

**Archivos mínimos:**
```
turn/domain/TurnAction.java
turn/domain/TurnResolutionResult.java
turn/domain/TurnCoordinatorResult.java
```

---

### F0-07 · DTOs de salida al cliente — contrato de API · [BLOQUEA → P3-01]

**Directorio:** `snapshot/dto/`

**Descripción**
Como frontend, necesito un contrato JSON estable que defina exactamente qué datos recibo tras cada turno resuelto.

**Criterios de aceptación:**
- `SnapshotDTO` contiene: `gameId`, `turn`, `status`, `myHp`, `myCol`, `myRow`, `myRegion`, `enemyRegion` (nunca posición exacta del rival), `impactAreaReceived` (celdas del ataque enemigo sobre mí), `hitOnMe` (HitResult), `damageReceived`, `myAttackArea`, `hitOnEnemy`, `winnerId` (nullable)
- `PositionDTO` contiene `col` y `row`
- Existe `snapshot_example.json` con un ejemplo válido y comentado
- Los DTOs nunca exponen la posición exacta del rival

**Archivos mínimos:**
```
snapshot/dto/SnapshotDTO.java
snapshot/dto/PositionDTO.java
snapshot/dto/snapshot_example.json
```

---

## PERSONA 1 — Infraestructura y runtime

**Responsabilidad:** estado en memoria, concurrencia, configuración Spring, persistencia MySQL y scheduler de timeout.

| # | Tarea | Depende de |
|---|---|---|
| P1-01 | ActiveGamesRegistry | F0-05 |
| P1-02 | GameMemoryStore | P1-01, F0-01 |
| P1-03 | LockExecutor | — |
| P1-04 | Configuración Spring | — |
| P1-05 | Entidad JPA y repositorio MySQL | — |
| P1-06 | GameLifecycleService | P1-05, P1-01 |
| P1-07 | TurnTimeoutScheduler | P1-01, P2-07 |
| P1-08 | Test de concurrencia | P1-03, P2-04 |
| P1-09 | GlobalExceptionHandler | F0-02, F0-01 |

---

### P1-01 · ActiveGamesRegistry · [DEPENDE DE F0-05] · [BLOQUEA → P1-02, P2-06, P3-02]

**Directorio:** `infrastructure/memory/`

**Descripción**
Como sistema, necesito un registro centralizado de todas las partidas activas en memoria, para que cualquier servicio pueda acceder al estado de una partida sin conocer la implementación del almacenamiento subyacente.

**Criterios de aceptación:**
- Encapsula un `ConcurrentHashMap<String, GameState>` sin exponerlo
- Expone: `save(GameState)`, `findById(String) → Optional<GameState>`, `remove(String)`, `getAllActive() → Collection<GameState>`
- Sin lógica de juego ni validación de estado
- Es un `@Component` sin dependencias adicionales
- Test unitario verifica: guardar, recuperar existente, recuperar inexistente devuelve vacío, eliminar

**Archivos mínimos:**
```
infrastructure/memory/ActiveGamesRegistry.java
infrastructure/memory/ActiveGamesRegistryTest.java
```

---

### P1-02 · GameMemoryStore — fachada de acceso · [DEPENDE DE P1-01, F0-01] · [BLOQUEA → P2-06, P2-08, P3-02]

**Directorio:** `infrastructure/memory/`

**Descripción**
Como servicio de dominio, necesito una fachada que encapsule el acceso a memoria y que lance una excepción tipada cuando una partida no existe, para no duplicar el manejo de `Optional` en cada servicio.

**Criterios de aceptación:**
- Envuelve `ActiveGamesRegistry`
- Expone: `getOrThrow(String gameId) → GameState` (lanza `GameException(GameErrorCode.GAME_NOT_FOUND)` si no existe), `save(GameState)`, `remove(String)`
- Los servicios de dominio dependen de `GameMemoryStore`, nunca de `ActiveGamesRegistry` directamente

**Archivos mínimos:**
```
infrastructure/memory/GameMemoryStore.java
```

---

### P1-03 · LockExecutor — ejecución con lock por partida · [BLOQUEA → P2-05, P2-07]

**Directorio:** `infrastructure/lock/`

**Descripción**
Como sistema concurrente, necesito un executor centralizado que adquiera y libere el lock de una partida de forma segura, para evitar fugas de lock ante excepciones y eliminar uso directo del `ReentrantLock` en los servicios.

**Criterios de aceptación:**
- `GameLockManager` expone `getLock(GameState) → ReentrantLock`
- `LockExecutor` expone `<T> T executeWithLock(GameState game, Supplier<T> action)` que garantiza `unlock()` en bloque `finally`
- No existe ningún `lock.lock()` / `lock.unlock()` fuera de `LockExecutor` en todo el proyecto
- Test unitario lanza dos threads concurrentes y verifica que el supplier se ejecuta secuencialmente

**Archivos mínimos:**
```
infrastructure/lock/GameLockManager.java
infrastructure/lock/LockExecutor.java
infrastructure/lock/LockExecutorTest.java
```

---

### P1-04 · Configuración técnica de Spring · [BLOQUEA → todos]

**Directorio:** `config/`

**Descripción**
Como sistema, necesito la configuración técnica base de Spring Boot activa desde el inicio del proyecto, para que el scheduler, el CORS y la serialización JSON funcionen correctamente.

**Criterios de aceptación:**
- `AsyncConfig` define un `ThreadPoolTaskExecutor` con core=2, max=4, queue=50
- `SchedulerConfig` habilita `@EnableScheduling` con pool de tamaño 2
- `CorsConfig` permite peticiones desde el origen configurado en `application.properties`
- `JacksonConfig` configura: fechas como timestamps, campos null omitidos, camelCase
- La aplicación arranca sin errores ni warnings de configuración

**Archivos mínimos:**
```
config/AsyncConfig.java
config/SchedulerConfig.java
config/CorsConfig.java
config/JacksonConfig.java
resources/application.properties
```

---

### P1-05 · Entidad JPA y repositorio MySQL · [BLOQUEA → P1-06, P3-05]

**Directorio:** `persistence/`

**Descripción**
Como sistema, necesito persistir los metadatos de cada partida en MySQL en exactamente dos momentos: al crearla y al terminarla, sin leer MySQL durante los turnos intermedios.

**Criterios de aceptación:**
- `GameEntity` tiene anotaciones JPA con campos: `gameId` (PK VARCHAR), `playerAId`, `playerBId`, `status` (String), `winnerId` (nullable), `turnsPlayed` (INT, default 0), `createdAt` (DATETIME)
- `GameRepository` extiende `JpaRepository<GameEntity, String>` sin métodos adicionales
- `GameMapper` convierte `GameState → GameEntity` para INSERT, y tiene `toFinishedEntity(GameEntity, String winnerId, int turnsPlayed)` para UPDATE
- La entidad JPA nunca se usa como dominio runtime
- El schema SQL está definido en `schema.sql`

**Archivos mínimos:**
```
persistence/entity/GameEntity.java
persistence/repository/GameRepository.java
persistence/mapper/GameMapper.java
resources/schema.sql
```

---

### P1-06 · GameLifecycleService — finalización de partida · [DEPENDE DE P1-05, P1-01] · [BLOQUEA → P2-08, P3-05]

**Directorio:** `game/service/`

**Descripción**
Como sistema, necesito un servicio que cierre una partida cuando termina: persistir el resultado en MySQL y eliminar el GameState de memoria.

**Criterios de aceptación:**
- `GameLifecycleService.finalize(String gameId, String winnerId, int turnsPlayed)` ejecuta UPDATE en MySQL vía `GameRepository` con `status = FINISHED`, `winner_id` y `turns_played`
- Después del UPDATE llama a `ActiveGamesRegistry.remove(gameId)`
- Si `winnerId == null` almacena la cadena `"draw"` en MySQL
- Si se llama con un `gameId` ya eliminado de memoria, no lanza excepción (idempotente)

**Archivos mínimos:**
```
game/service/GameLifecycleService.java
```

---

### P1-07 · TurnTimeoutScheduler · [DEPENDE DE P1-01, P2-07] · [BLOQUEA → INT-4]

**Directorio:** `infrastructure/scheduler/`

**Descripción**
Como sistema, necesito un proceso periódico que detecte turnos que superaron el tiempo límite y fuerce su resolución, sin modificar el GameState directamente.

**Criterios de aceptación:**
- `TurnTimeoutScheduler` usa `@Scheduled(fixedDelayString = "${turn.scheduler.interval.ms:5000}")`
- Lee partidas activas mediante `ActiveGamesRegistry.getAllActive()` (solo lectura)
- Para cada partida con `status == ACTIVE`, `pendingActions.size() == 1` y `(now - firstActionReceivedAt) > ${turn.timeout.ms:30000}` → llama `TurnTimeoutService.forceResolveTimeout(gameId)`
- El scheduler **nunca** toca `pendingActions` ni `game.lock` directamente

**Archivos mínimos:**
```
infrastructure/scheduler/TurnTimeoutScheduler.java
```

---

### P1-08 · Test de concurrencia — doble resolución · [DEPENDE DE P1-03, P2-04]

**Directorio:** `infrastructure/`

**Descripción**
Como equipo, necesitamos verificar que el locking impide que dos threads resuelvan el mismo turno simultáneamente.

**Criterios de aceptación:**
- El test lanza dos threads concurrentes que intentan agregar la acción del mismo jugador en el mismo turno
- Solo una acción es aceptada; la segunda recibe rechazo por deduplicación (`pendingActions.containsKey`)
- `TurnResolver.resolve()` se ejecuta exactamente una vez
- No hay excepciones no controladas durante la ejecución concurrente

**Archivos mínimos:**
```
infrastructure/ConcurrencyTest.java
```

---

### P1-09 · GlobalExceptionHandler — manejo uniforme de errores HTTP · [DEPENDE DE F0-02, F0-01]

**Directorio:** `shared/exception/`

**Descripción**
Como cliente de la API, quiero recibir respuestas de error en formato JSON consistente para cualquier tipo de error, para poder manejarlos desde el frontend sin parsear mensajes crudos de excepción.

**Criterios de aceptación:**
- `GlobalExceptionHandler` con `@RestControllerAdvice` captura `GameException` y devuelve `{ "error": "...", "code": "..." }` con el status HTTP según el `errorCode`; usa `GameErrorCode` para el switch, sin Strings hardcodeados:
  - `GAME_NOT_FOUND` → 404
  - `INVALID_MOVE` → 400
  - `OUT_OF_BOUNDS` → 400
  - `INVALID_ATTACK` → 400
  - `STALE_TURN` → 400
  - `DUPLICATE_ACTION` → 400
  - `SELF_JOIN` → 400
  - `GAME_NOT_ACTIVE` → 409
  - `GAME_FULL` → 409
- Captura `MethodArgumentNotValidException` → 400 con lista de campos inválidos
- Captura cualquier otra excepción → 500 con `{ "error": "Error interno del servidor" }` sin exponer stack traces
- Cubierto con test de integración mínimo que verifica al menos dos casos de error

**Archivos mínimos:**
```
shared/exception/GlobalExceptionHandler.java
shared/exception/ErrorResponse.java
shared/exception/GlobalExceptionHandlerTest.java
```

---

## PERSONA 2 — Motor de juego y coordinación de turno

**Responsabilidad:** engine determinista, validadores, coordinación de turno, timeout service y orquestación.

| # | Tarea | Depende de |
|---|---|---|
| P2-01 | MovementEngine | F0-04, F0-05, F0-01 |
| P2-02 | ImpactResolver | F0-03, F0-04 |
| P2-03 | DamageCalculator | F0-05 |
| P2-04 | TurnResolver | P2-01, P2-02, P2-03 |
| P2-05 | Validadores de acción | F0-02, F0-04, F0-05, F0-06, F0-01 |
| P2-06 | TurnCoordinator | P2-04, P2-05, P1-03 |
| P2-07 | TurnTimeoutService | P1-03, P2-06 |
| P2-08 | TurnSubmissionService | P2-06, P1-02, P1-06 |
| P2-09 | Tests del engine | P2-04 |

---

### P2-01 · MovementEngine — aplicar movimiento · [DEPENDE DE F0-04, F0-05, F0-01]

**Directorio:** `game/engine/`

**Descripción**
Como motor de juego, necesito aplicar el movimiento declarado por un jugador a su `PlayerState`, verificando distancia Chebyshev y límites del tablero.

**Criterios de aceptación:**
- `MovementEngine.applyMovement(PlayerState player, int toCol, int toRow)` actualiza `posCol` y `posRow` si el movimiento es válido
- Lanza `GameException(GameErrorCode.OUT_OF_BOUNDS)` primero si las coordenadas destino están fuera de `[0, 14]` (validación de límites tiene prioridad)
- Lanza `GameException(GameErrorCode.INVALID_MOVE)` si el destino es válido en tablero pero la distancia Chebyshev excede 4
- Quedarse quieto es válido
- Usa `GridUtils` sin reimplementar lógica
- Test cubre: movimiento recto válido, diagonal válido, quedarse quieto, exceder distancia, salir del tablero en cada borde

**Archivos mínimos:**
```
game/engine/MovementEngine.java
game/engine/MovementEngineTest.java
```

---

### P2-02 · ImpactResolver — área de impacto y detección de hit · [DEPENDE DE F0-03, F0-04]

**Directorio:** `game/engine/`

**Descripción**
Como motor de juego, necesito calcular el área 3×3 de un ataque y determinar si el rival se encuentra dentro de esa área tras aplicar su movimiento.

**Criterios de aceptación:**
- `ImpactResolver.resolveImpact(int attackCol, int attackRow, PlayerState target) → HitResult` devuelve `DIRECT_HIT` si el target está en la celda central exacta del ataque, `HIT` si está en el área 3×3 pero no en el centro, `MISS` si no está en el área
- `ImpactResolver.computeImpactArea(int centerCol, int centerRow) → ImpactArea` devuelve las celdas válidas del área 3×3
- Usa `GridUtils.computeImpactArea` e `ImpactArea.contains` sin reimplementar
- Test cubre: hit en centro exacto, hit en borde del área, miss, ataque en esquina del tablero, ataque en borde lateral

**Archivos mínimos:**
```
game/engine/ImpactResolver.java
game/engine/ImpactResolverTest.java
```

---

### P2-03 · DamageCalculator — daño estándar y bono Sniper · [DEPENDE DE F0-05]

**Directorio:** `game/engine/`

**Descripción**
Como motor de juego, necesito calcular el daño que inflige un jugador según si se movió o no, y aplicarlo al HP del rival.

**Criterios de aceptación:**
- `DamageCalculator.calculateDamage(boolean playerMoved)` devuelve `25` si se movió, `35` si no
- `DamageCalculator.applyDamage(PlayerState target, int damage)` reduce `target.hp` sin que quede negativo (mínimo 0)
- Sin dependencias de Spring ni de infraestructura
- Test cubre: daño estándar, bono Sniper, reducción a exactamente 0, reducción que sobrepasa 0

**Archivos mínimos:**
```
game/engine/DamageCalculator.java
game/engine/DamageCalculatorTest.java
```

---

### P2-04 · TurnResolver — resolución completa del turno · [DEPENDE DE P2-01, P2-02, P2-03] · [BLOQUEA → P2-06, P1-08, P2-09]

**Directorio:** `game/engine/`

**Descripción**
Como motor de juego, necesito ejecutar la resolución completa de un turno en orden estricto y de forma determinista.

**Criterios de aceptación:**
- `TurnResolver.resolve(GameState game, TurnAction actionA, TurnAction actionB) → TurnResolutionResult` ejecuta en orden:
  1. Aplicar movimiento de ambos jugadores
  2. Calcular área de impacto de cada ataque
  3. Detectar si cada jugador es impactado por el rival
  4. Calcular daño con bono Sniper si no hubo movimiento
  5. Aplicar daño a cada PlayerState
  6. Incrementar `game.turnNumber`
  7. Determinar `gameOver` y `winnerId`
  8. Retornar `TurnResolutionResult` completo
- No toca `pendingActions` (responsabilidad del coordinador)
- Sin dependencias de Spring ni de infraestructura
- Determinista: mismo input → mismo output siempre

**Archivos mínimos:**
```
game/engine/TurnResolver.java
```

---

### P2-05 · Validadores de acción · [DEPENDE DE F0-02, F0-04, F0-05, F0-06, F0-01] · [BLOQUEA → P2-06]

**Directorio:** `game/validation/`

**Descripción**
Como sistema, necesito validar las acciones de turno antes de aceptarlas, para rechazar movimientos inválidos, ataques fuera de tablero y acciones extemporáneas sin mezclar esta lógica con el engine.

**Criterios de aceptación:**
- `MovementValidator.validate(PlayerState current, int toCol, int toRow)` lanza `GameException(GameErrorCode.OUT_OF_BOUNDS)` si el destino está fuera de `[0, 14]`, y `GameException(GameErrorCode.INVALID_MOVE)` si la distancia Chebyshev excede 4 (mismo orden que MovementEngine)
- `AttackValidator.validate(int attackCol, int attackRow)` lanza `GameException(GameErrorCode.INVALID_ATTACK)` si el punto central está fuera del tablero
- `TurnValidator.validate(GameState game, TurnAction action)` lanza `GameException` según: `status != ACTIVE` → `GameErrorCode.GAME_NOT_ACTIVE`; `action.turn != game.turnNumber` → `GameErrorCode.STALE_TURN`; `playerId` ya en `pendingActions` → `GameErrorCode.DUPLICATE_ACTION`
- Cada validador tiene tests independientes para cada caso de rechazo y para el caso válido

**Archivos mínimos:**
```
game/validation/MovementValidator.java
game/validation/AttackValidator.java
game/validation/TurnValidator.java
game/validation/MovementValidatorTest.java
game/validation/AttackValidatorTest.java
game/validation/TurnValidatorTest.java
```

---

### P2-06 · TurnCoordinator — acumular acciones y disparar resolución · [DEPENDE DE P2-04, P2-05, P1-03] · [BLOQUEA → P2-07, P2-08]

**Directorio:** `turn/service/`

**Descripción**
Como sistema, necesito un coordinador que reciba la acción de cada jugador, la valide, la acumule en `pendingActions` y ejecute la resolución cuando ambos jugadores hayan actuado, todo dentro del lock de la partida.

**Criterios de aceptación:**
- `TurnCoordinator.submitAction(GameState game, TurnAction action) → TurnCoordinatorResult` ejecuta dentro de `LockExecutor`:
  1. Llama a los tres validadores
  2. Agrega `action` a `game.pendingActions`
  3. Si es la primera acción: registra `game.firstActionReceivedAt`
  4. Si `pendingActions.size() == 2`: cambia `status = RESOLVING`, llama `TurnResolver.resolve()`, limpia `pendingActions`, actualiza `status = ACTIVE` o `FINISHED`, retorna `TurnCoordinatorResult` con `resolved = true`
  5. Si solo hay una acción: retorna `TurnCoordinatorResult` con `waiting = true`

**Archivos mínimos:**
```
turn/service/TurnCoordinator.java
turn/service/TurnCoordinatorTest.java
```

---

### P2-07 · TurnTimeoutService — resolución forzada por timeout · [DEPENDE DE P1-03, P2-06] · [BLOQUEA → P1-07]

**Directorio:** `turn/service/`

**Descripción**
Como sistema, necesito un servicio que fuerce la resolución de un turno cuando un jugador no envió su acción dentro del tiempo límite, para que las partidas no queden bloqueadas indefinidamente.

**Criterios de aceptación:**
- `TurnTimeoutService.forceResolveTimeout(String gameId)` genera una `TurnAction` por defecto para el jugador ausente: misma posición actual (sin movimiento), ataque en `(-1, -1)` que resulta en `MISS`
- Adquiere el lock mediante `LockExecutor` antes de modificar `pendingActions`
- **Dentro del lock**, verifica que `game.status == ACTIVE && game.pendingActions.size() == 1` antes de inyectar la acción; si no se cumple, retorna sin hacer nada (el turno ya fue resuelto por la acción real del jugador)
- Delega la resolución a `TurnCoordinator.submitAction()`, sin reimplementar lógica de resolución
- Si la partida no existe o ya fue resuelta, retorna sin error

- Test `timeout_ignorado_si_turno_ya_resuelto`: si `forceResolveTimeout` se llama cuando `pendingActions.size() == 2` o `status != ACTIVE`, no se inyecta ninguna acción adicional ni se llama a `TurnCoordinator`

**Archivos mínimos:**
```
turn/service/TurnTimeoutService.java
turn/service/TurnTimeoutServiceTest.java
```

---

### P2-08 · TurnSubmissionService — orquestación de envío · [DEPENDE DE P2-06, P1-02, P1-06]

**Directorio:** `game/service/`

**Descripción**
Como sistema, necesito un servicio que orqueste el flujo completo al recibir una acción: cargar el GameState, delegar al coordinador y cerrar la partida si terminó.

**Criterios de aceptación:**
- `TurnSubmissionService.submit(String gameId, TurnAction action) → TurnCoordinatorResult` carga el `GameState` con `GameMemoryStore.getOrThrow()`, delega a `TurnCoordinator.submitAction()`, y si `resolutionResult.gameOver == true` llama a `GameLifecycleService.finalize()`
- No contiene lógica matemática ni de resolución
- Propaga cualquier `GameException` tal cual sin envolverla

**Archivos mínimos:**
```
game/service/TurnSubmissionService.java
```

---

### P2-09 · Tests del engine — escenarios deterministas · [DEPENDE DE P2-04]

**Directorio:** `game/engine/`

**Descripción**
Como equipo, necesitamos verificar que el motor de resolución produce resultados correctos y reproducibles para los escenarios principales del juego.

**Criterios de aceptación:**
- `hit_estandar`: jugador A se mueve y su ataque impacta pero no en celda central → `damageToB = 25`, `hitResultA = HIT`
- `direct_hit`: jugador A ataca y el rival está exactamente en la celda central → `hitResultA = DIRECT_HIT`
- `sniper_bonus`: jugador A no se mueve y su ataque impacta → `damageToB = 35`
- `miss`: jugador A ataca pero el rival no está en el área → `damageToB = 0`, `hitResultA = MISS`
- `empate_mutuo`: ambos quedan en 0 HP en el mismo turno → `gameOver = true`, `winnerId = null`
- `victoria_clara`: un jugador queda en 0 HP, el otro no → `winnerId` correcto
- `impacto_en_esquina`: ataque en esquina genera área parcial sin error
- Todos puramente unitarios, sin Spring context ni mocks de infraestructura

**Archivos mínimos:**
```
game/engine/TurnResolverTest.java
```

---

## PERSONA 3 — Snapshot, lobby y contrato HTTP

**Responsabilidad:** conversión de estado a respuesta cliente, endpoints de polling, lobby completo y reconexión.

| # | Tarea | Depende de |
|---|---|---|
| P3-01 | SnapshotFactory | F0-07, F0-04 |
| P3-02 | SnapshotService | P3-01, P1-01, F0-01 |
| P3-03 | GameController — polling y reconexión | P3-02 |
| P3-04 | TurnController — submit | P2-08, P3-01 |
| P3-05 | Lobby — crear, join e iniciar partida | P1-05, P1-01, F0-01 |
| P3-06 | LobbyController | P3-05 |
| P3-07 | Tests de snapshot | P3-01, P2-04 |
| P3-08 | Tests de integración del lobby | P3-05, P3-06 |
| P3-09 | Tests de integración de turno | P3-04, P2-06 |

---

### P3-01 · SnapshotFactory — GameState → SnapshotDTO · [DEPENDE DE F0-07, F0-04] · [BLOQUEA → P3-02, P3-04]

**Directorio:** `snapshot/factory/`

**Descripción**
Como sistema, necesito un único punto de conversión entre el estado interno de la partida y la respuesta que se envía al cliente, garantizando que nunca se filtre la posición exacta del rival.

**Criterios de aceptación:**
- `SnapshotFactory.buildSnapshot(GameState game, TurnResolutionResult result, String requestingPlayerId) → SnapshotDTO` construye el DTO personalizado para el jugador solicitante
- Incluye posición exacta y HP del jugador propio
- Incluye solo la **región** del rival (`Region.toLabel()`), nunca sus coordenadas exactas
- Incluye el área de impacto del ataque enemigo recibido y el área del propio ataque
- Test verifica que `SnapshotDTO` no expone la posición exacta del rival bajo ninguna circunstancia
- Test verifica que la perspectiva de playerA y playerB son simétricas y correctas

**Archivos mínimos:**
```
snapshot/factory/SnapshotFactory.java
snapshot/factory/SnapshotFactoryTest.java
```

---

### P3-02 · SnapshotService — recuperar snapshot por polling · [DEPENDE DE P3-01, P1-01, F0-01]

**Directorio:** `snapshot/service/`

**Descripción**
Como cliente en modo polling, necesito consultar el estado actual de la partida para saber si el turno fue resuelto y recibir el snapshot actualizado.

**Criterios de aceptación:**
- `SnapshotService.getSnapshot(String gameId, String playerId) → Optional<SnapshotDTO>` devuelve el SnapshotDTO si el turno ya fue resuelto, o `Optional.empty()` si el turno sigue pendiente
- `SnapshotService.getGameStatus(String gameId) → GameStatus` devuelve el status sin construir el SnapshotDTO completo
- No modifica el `GameState`, solo lo lee
- Si la partida no existe → lanza `GameException(GameErrorCode.GAME_NOT_FOUND)`

**Archivos mínimos:**
```
snapshot/service/SnapshotService.java
```

---

### P3-03 · GameController — polling y reconexión · [DEPENDE DE P3-02]

**Directorio:** `game/controller/`

**Descripción**
Como cliente en polling, quiero consultar el estado actual de la partida para actualizar mi UI cuando el turno sea resuelto, y poder recuperar el último snapshot si me reconecto a mitad de una partida.

**Criterios de aceptación:**
- `GET /api/game/{gameId}/state` requiere header `X-Player-Id`
- Si el turno ya fue resuelto → 200 con `SnapshotDTO`
- Si el turno sigue pendiente → 200 con `{ "waiting": true, "status": "ACTIVE" }`
- Si la partida no existe → 404
- Si `X-Player-Id` está ausente → 400
- `GET /api/game/{gameId}/snapshot/last` devuelve el último snapshot disponible independientemente del estado del turno actual; si no hay ningún turno resuelto aún → 200 con `{ "status": "WAITING", "turn": 0 }`
- Ambos endpoints delegan completamente a `SnapshotService`

**Archivos mínimos:**
```
game/controller/GameController.java
```

---

### P3-04 · TurnController — POST /api/turn/submit · [DEPENDE DE P2-08, P3-01]

**Directorio:** `game/controller/`

**Descripción**
Como jugador, quiero enviar mi acción de turno al servidor mediante HTTP, para que el servidor la acumule y resuelva el turno cuando ambos jugadores hayan actuado.

**Criterios de aceptación:**
- `POST /api/turn/submit` recibe: `{ gameId, playerId, turn, moveToCol, moveToRow, attackCol, attackRow }`
- Si `waiting == true` → 200 con `{ "received": true, "waiting": true }`
- Si el turno fue resuelto → 200 con el `SnapshotDTO` del jugador que envió la acción
- Si falla validación → 400 con `{ "error": "...", "code": "..." }`
- Si `status != ACTIVE` → 409
- El controller no contiene lógica: delega a `TurnSubmissionService` y `SnapshotFactory`

**Archivos mínimos:**
```
game/controller/TurnController.java
game/dto/SubmitActionRequest.java
```

---

### P3-05 · LobbyService — crear, unirse e iniciar partida · [DEPENDE DE P1-05, P1-01, F0-01] · [BLOQUEA → P3-06]

**Directorio:** `lobby/service/`

**Descripción**
Como jugador, quiero poder crear una partida, unirme a una existente e iniciarla cuando ambos jugadores estén listos, para comenzar a enviar acciones de turno.

**Criterios de aceptación:**
- `LobbyService.create(String playerId) → CreateGameResponse` genera un `gameId` único, crea un `GameState` con `status = WAITING` y `playerA` en posición `(2, 2)` con 100 HP, lo guarda en `ActiveGamesRegistry` y hace INSERT en MySQL vía `GameRepository`
- `LobbyService.join(String gameId, String playerId) → JoinGameResponse` agrega al jugador como `playerB` en posición `(12, 12)` con 100 HP; lanza `GameException(GameErrorCode.GAME_NOT_FOUND)` si no existe, `GameException(GameErrorCode.GAME_FULL)` si ya tiene dos jugadores, `GameException(GameErrorCode.SELF_JOIN)` si `playerId == playerA.playerId`
- `LobbyService.start(String gameId) → StartGameResponse` cambia `status = ACTIVE` y `turnNumber = 1`; lanza `GameException(GameErrorCode.GAME_NOT_ACTIVE)` si `status != WAITING`
- Ningún método contiene lógica de turno ni de resolución

**Archivos mínimos:**
```
lobby/service/LobbyService.java
lobby/dto/CreateGameRequest.java
lobby/dto/CreateGameResponse.java
lobby/dto/JoinGameRequest.java
lobby/dto/JoinGameResponse.java
lobby/dto/StartGameRequest.java
lobby/dto/StartGameResponse.java
```

---

### P3-06 · LobbyController — endpoints de lobby · [DEPENDE DE P3-05]

**Directorio:** `lobby/controller/`

**Descripción**
Como cliente, quiero acceder a los endpoints de creación, unión e inicio de partida mediante HTTP.

**Criterios de aceptación:**
- `POST /api/lobby/create` recibe `CreateGameRequest` y devuelve `CreateGameResponse`
- `POST /api/lobby/join` recibe `JoinGameRequest` y devuelve `JoinGameResponse`
- `POST /api/lobby/start` recibe `StartGameRequest` y devuelve `StartGameResponse`
- Todos los casos de error son manejados por `GlobalExceptionHandler` (el controller no tiene try-catch)
- El controller no contiene lógica: delega completamente a `LobbyService`

**Archivos mínimos:**
```
lobby/controller/LobbyController.java
```

---

### P3-07 · Tests de snapshot · [DEPENDE DE P3-01, P2-04]

**Directorio:** `snapshot/`

**Descripción**
Como equipo, necesitamos verificar que `SnapshotFactory` construye correctamente la perspectiva de cada jugador y que la información del rival está correctamente restringida.

**Criterios de aceptación:**
- `snapshot_perspectiva_playerA`: dado un `TurnResolutionResult` conocido, el snapshot de playerA incluye su posición real y la región de playerB, nunca la posición exacta del rival
- `snapshot_perspectiva_playerB`: misma verificación desde perspectiva de playerB
- `snapshot_no_filtra_posicion_enemiga`: verifica explícitamente que ningún campo del DTO contiene las coordenadas exactas del rival
- `snapshot_partida_terminada`: cuando `gameOver == true`, `winnerId` está presente en el DTO
- `snapshot_empate`: cuando `winnerId == null` y `gameOver == true`, el campo en el DTO es `"draw"`

**Archivos mínimos:**
```
snapshot/factory/SnapshotIntegrationTest.java
```

---

### P3-08 · Tests de integración del lobby · [DEPENDE DE P3-05, P3-06]

**Directorio:** `lobby/`

**Descripción**
Como equipo, necesitamos verificar el flujo completo de lobby mediante tests de integración con Spring context.

**Criterios de aceptación:**
- Test `crear_partida`: `POST /api/lobby/create` devuelve 200 con `gameId` y `status = WAITING`
- Test `join_exitoso`: `POST /api/lobby/join` con gameId válido devuelve 200 con `status = WAITING`
- Test `join_partida_inexistente`: devuelve 404
- Test `join_partida_llena`: devuelve 409
- Test `start_exitoso`: `POST /api/lobby/start` devuelve 200 con `status = ACTIVE` y `turn = 1`
- Test `start_sin_dos_jugadores`: devuelve 409

**Archivos mínimos:**
```
lobby/LobbyIntegrationTest.java
```

---

### P3-09 · Tests de integración de turno · [DEPENDE DE P3-04, P2-06]

**Directorio:** `game/`

**Descripción**
Como equipo, necesitamos verificar el flujo HTTP completo de envío de acciones y polling para un turno completo.

**Criterios de aceptación:**
- Test `submit_primera_accion`: `POST /api/turn/submit` con una sola acción devuelve 200 con `{ "waiting": true }`
- Test `submit_segunda_accion_resuelve`: la segunda acción del mismo turno devuelve 200 con `SnapshotDTO` completo
- Test `submit_accion_duplicada`: enviar dos veces la misma acción del mismo jugador devuelve 400 con `code = DUPLICATE_ACTION`
- Test `submit_turno_incorrecto`: enviar acción con `turn` equivocado devuelve 400 con `code = STALE_TURN`
- Test `polling_turno_pendiente`: `GET /api/game/{gameId}/state` mientras se espera la segunda acción devuelve `{ "waiting": true }`
- Test `polling_turno_resuelto`: después de resolver el turno devuelve `SnapshotDTO`

**Archivos mínimos:**
```
game/TurnIntegrationTest.java
```

---

## INTEGRACIONES

---

### INT-1 · Contrato TurnResolutionResult ↔ SnapshotFactory · P2 + P3

**Prerrequisitos:** F0-06, F0-07, P2-04 (borrador), P3-01 (borrador)

**Objetivo:** verificar que todos los campos que `SnapshotFactory` necesita están presentes en `TurnResolutionResult`. Ajustar antes de que ambas partes finalicen sus implementaciones.

**Artefacto:** `snapshot_example.json` actualizado y aprobado por ambas partes.

---

### INT-2 · LockExecutor + TurnCoordinator · P1 + P2

**Prerrequisitos:** P1-03, P2-06

**Objetivo:** integrar `LockExecutor` dentro de `TurnCoordinator.submitAction()` y verificar con `P1-08` que el locking previene doble resolución.

**Artefacto:** `ConcurrencyTest.java` pasando.

---

### INT-3 · Flujo completo de un turno · Todos

**Prerrequisitos:** P1-08 (start), P2-08 (TurnSubmissionService), P3-04 (TurnController), P3-03 (polling)

**Objetivo:** ejecutar un turno completo end-to-end con curl o Postman: crear → join → start → submit A → submit B → polling → snapshot de ambos jugadores.

**Artefacto:** colección Postman o script curl documentado.

---

### INT-4 · Flujo con timeout · P1 + P2

**Prerrequisitos:** P1-07 (scheduler), P2-07 (TurnTimeoutService)

**Objetivo:** verificar que si solo un jugador envía su acción y se espera más de `turn.timeout.ms`, el scheduler fuerza la resolución y la partida avanza correctamente.

**Artefacto:** test de integración o verificación manual documentada.

---

## Resumen de conteo

| Persona | Tareas |
|---|---|
| Fase 0 (conjunta) | 7 |
| Persona 1 | 9 |
| Persona 2 | 9 |
| Persona 3 | 9 |
| **Total** | **34** |

---

## Resumen de dependencias críticas

| Tarea bloqueante | Desbloquea |
|---|---|
| F0-01 Enums compartidos | F0-02, P1-02, P1-09, P2-01, P2-05, P3-02, P3-05 |
| F0-04 GridUtils | P2-01, P2-02, P2-05 |
| F0-05 GameState | P1-01, P2-01, P2-03 |
| F0-06 TurnAction | P2-04, P2-05 |
| F0-07 SnapshotDTO | P3-01 |
| P1-01 ActiveGamesRegistry | P1-02, P2-06, P3-02 |
| P1-03 LockExecutor | P2-06, P2-07 |
| P1-05 GameRepository | P1-06, P3-05 |
| P2-04 TurnResolver | P2-06, P1-08, P2-09 |
| P2-06 TurnCoordinator | P2-07, P2-08 |
| P3-01 SnapshotFactory | P3-02, P3-04 |
| P3-05 LobbyService | P3-06, P3-08 |
