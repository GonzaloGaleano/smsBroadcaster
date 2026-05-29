# CLAUDE.md — SMS Gateway Android (ok2app)

Contexto de proyecto para asistente IA. Leé esto antes de cualquier tarea de código.

---

## Qué es este proyecto

App Android nativa (Kotlin + Jetpack Compose) que funciona como **SMS Gateway privado** para el SaaS ok2app. El dispositivo Android actúa como modem: consulta mensajes pendientes en una API Laravel, los envía por SMS usando la SIM del teléfono, y reporta el resultado a la API. **App privada — distribución por APK sideload, no Google Play.**

---

## Stack y versiones exactas

| Componente | Versión |
|---|---|
| AGP | 9.2.1 |
| Kotlin | 2.1.10 |
| KSP | 2.1.10-1.0.29 |
| Compose BOM | 2026.02.01 |
| Room | 2.7.1 |
| Retrofit | 2.11.0 |
| OkHttp | 4.12.0 |
| WorkManager | 2.10.1 |
| DataStore | 1.1.4 |
| Navigation Compose | 2.9.0 |
| Coroutines | 1.10.2 |
| minSdk | 24 |
| targetSdk | 36 |

**Package base:** `com.ok2app.sms`

---

## Estructura de paquetes

```
app/src/main/java/com/ok2app/sms/
│
├── SmsGatewayApp.kt          Application class — singletons lazy (preferences, database, repository)
├── MainActivity.kt            Punto de entrada — NavHost + solicitud permisos SEND_SMS / POST_NOTIFICATIONS
│
├── data/
│   ├── local/
│   │   ├── db/
│   │   │   ├── SmsDatabase.kt      Room DB v1 — exportSchema=true, schemas en /app/schemas/
│   │   │   ├── SmsLogEntity.kt     @Entity("sms_log") — remoteId PK, recipient, statusCode, errorMessage, processedAt, retryCount
│   │   │   └── SmsLogDao.kt        getRecentLogs() Flow, getCountByStatus() Flow, getRecentSentCount() suspend, insertOrUpdate()
│   │   └── prefs/
│   │       └── GatewayPreferences.kt  DataStore Preferences — baseUrl, apiToken, gatewayId, isPaused, maxMessagesPerMinute(default=5)
│   │                                   Expone configFlow: Flow<GatewayConfig>
│   ├── remote/
│   │   ├── api/GatewayApiService.kt   Retrofit interface — 5 endpoints (ver contrato abajo)
│   │   ├── dto/Requests.kt            RegisterRequest, HeartbeatRequest, StatusUpdateRequest
│   │   ├── dto/Responses.kt           RegisterResponse(gatewayId), PendingMessageDto, PendingMessagesResponse
│   │   └── RetrofitClient.kt          Factory: create(baseUrl, token) → añade Bearer auth header, logging, 30s timeouts
│   └── repository/
│       └── GatewayRepository.kt       register(), sendHeartbeat(), runPollCycle(): PollResult
│                                       Expone: configFlow, recentLogs, stats: Flow<GatewayStats>
│
├── domain/model/
│   ├── GatewayConfig.kt      baseUrl, apiToken, gatewayId?, isPaused, maxMessagesPerMinute
│   │                          computed: isConfigured (todos no vacíos + gatewayId != null), isReadyToSend
│   └── SmsMessage.kt         id, recipient, content, priority
│
├── receiver/
│   └── BootReceiver.kt       BOOT_COMPLETED → inicia SmsForegroundService si isConfigured
│
├── service/
│   ├── SmsForegroundService.kt   START_STICKY, foregroundServiceType=dataSync
│   │                              Loop coroutine cada 60s: si !isPaused → runPollCycle()
│   │                              Notificación persistente con acciones Pausar/Reanudar
│   │                              onCreate() inicia WorkManager watchdog (15 min)
│   │                              Companion: start(ctx), stop(ctx), ACTION_PAUSE, ACTION_RESUME
│   └── SmsSender.kt              send(recipient, content): SmsResult (Success | Failure(reason))
│                                  Usa SmsManager, registra BroadcastReceiver dinámico para SENT intent
│                                  Timeout 30s. Divide mensajes largos con divideMessage()
│
├── worker/
│   ├── GatewayWatchdogWorker.kt  CoroutineWorker — verifica que service esté activo, envía heartbeat
│   └── RateLimiter.kt            canSend(maxPerMinute): sliding window 60s sobre SmsLogDao.getRecentSentCount()
│
└── ui/
    ├── navigation/NavGraph.kt     NavHost: setup → dashboard ↔ history
    │                              startDestination determinado por produceState leyendo configFlow.first()
    └── screens/
        ├── setup/
        │   ├── SetupViewModel.kt  AndroidViewModel — connect(): valida → register() → navega
        │   └── SetupScreen.kt     Inputs: baseUrl, apiToken, maxPerMinute (Slider 1-20)
        ├── dashboard/
        │   ├── DashboardViewModel.kt  combine(configFlow, stats, syncTime, isSyncing, lastError) → StateFlow<DashboardUiState>
        │   │                           init: inicia SmsForegroundService si isConfigured
        │   │                           togglePause(), syncNow()
        │   └── DashboardScreen.kt     ConnectionCard (dot status), StatsCard (pending/sent/failed), ControlsCard (switch + botón sync)
        └── history/
            ├── HistoryViewModel.kt    recentLogs.stateIn(WhileSubscribed(5000))
            └── HistoryScreen.kt       LazyColumn con SmsLogItem — StatusBadge por color (sent=verde, failed=rojo, sending=naranja)
```

---

## Contrato de API (endpoints Laravel)

Ver detalle completo en `docs/API_CONTRACT.md`. Resumen:

| Método | Path | Descripción |
|---|---|---|
| POST | `/api/sms-gateway/register` | Registra dispositivo, retorna `gateway_id` |
| POST | `/api/sms-gateway/heartbeat` | Latido con `{gateway_id, status}` |
| GET | `/api/sms-gateway/messages/pending` | Lista mensajes en estado `pending` |
| POST | `/api/sms-gateway/messages/{id}/claim` | Reclama mensaje (pending → claimed) |
| POST | `/api/sms-gateway/messages/{id}/status` | Actualiza estado final `{status, error_message?}` |

Auth: `Authorization: Bearer {apiToken}` en todos los requests.

---

## Base de datos local (Room)

Tabla `sms_log`:
```
remoteId (PK) | recipient | statusCode | errorMessage | processedAt (epoch ms) | retryCount
```

Valores de `statusCode`: `pending`, `claimed`, `sending`, `sent`, `failed`, `cancelled`

Schema exportado a: `app/schemas/com.ok2app.sms.data.local.db.SmsDatabase/1.json`

---

## Flujo de procesamiento de mensajes

```
API: pending
    → claim (POST /claim)           remoteId guardado localmente como "sending"
    → SmsSender.send()              SmsManager + SENT broadcast intent (timeout 30s)
    → updateStatus (POST /status)   "sent" o "failed" con errorMessage
    → log local actualizado
```

RateLimiter corta el loop si `sentCount(últimos 60s) >= maxMessagesPerMinute`.

---

## Decisiones de arquitectura

- **Sin Hilt**: DI manual vía Application class. Singletons lazy en `SmsGatewayApp`.
- **ForegroundService + WorkManager**: Service para polling activo (60s), WorkManager watchdog cada 15 min (mínimo de `PeriodicWorkRequest`).
- **SENT intent** (no DELIVERED): más rápido, no depende del operador destino.
- **SIM por defecto**: sin selección de SIM (MVP). Multi-SIM es trabajo futuro.
- **DataStore** (no SharedPreferences): API reactiva con Flow.

---

## Problemas de build conocidos y sus soluciones

### KSP + AGP 9.x incompatibility
**Error:** `Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin.`
**Causa:** AGP 9.x tiene Kotlin built-in y restringe `kotlin.sourceSets`. KSP registra sus fuentes generadas via ese DSL.
**Fix aplicado:** `gradle.properties` → `android.disallowKotlinSourceSets=false`

---

## Estado del MVP

### Implementado (Android)
- [x] ForegroundService con polling loop 60s
- [x] WorkManager watchdog 15 min + heartbeat
- [x] Registro de gateway y persistencia de config
- [x] Rate limiting configurable (sliding window)
- [x] SmsSender con SENT intent y timeout
- [x] Room log local (últimas 100 entradas)
- [x] Setup screen (URL + token + max/min)
- [x] Dashboard (estado, stats, pause/resume, sync manual)
- [x] History screen
- [x] Autostart en boot

### Pendiente (Laravel backend)
- [ ] Migraciones: `sms_gateways`, `sms_messages`, `sms_delivery_logs`
- [ ] Controladores y rutas para los 5 endpoints
- [ ] Autenticación del gateway (token por dispositivo)
- [ ] Estados y transiciones de `sms_messages`

### Futuras mejoras (Android)
- [ ] Selección de SIM (multi-SIM)
- [ ] Configuración del intervalo de polling (hoy hardcoded a 60s)
- [ ] Pantalla de configuración avanzada (retry, backoff)
- [ ] Export/share de logs
- [ ] Tests unitarios (Repository, RateLimiter, SmsSender)

---

## Convenciones del proyecto

- ViewModels: `AndroidViewModel` (necesitan Application para acceder a `SmsGatewayApp`)
- Flows: `StateFlow` con `SharingStarted.WhileSubscribed(5_000)` en ViewModels
- Coroutines: `SupervisorJob() + Dispatchers.IO` en SmsForegroundService
- Strings UI: en español (app interna)
- No Hilt: no agregar sin discutir primero, hay dependencias encadenadas
