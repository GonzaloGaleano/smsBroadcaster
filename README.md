# SMS Gateway — ok2app

App Android nativa que funciona como **gateway privado de envío SMS** para el SaaS ok2app. El dispositivo Android actúa como modem: consulta una cola de mensajes en el servidor Laravel, los envía por SMS usando la SIM del teléfono, y reporta el resultado a la API.

> **App privada.** Distribuida por APK sideload para uso interno del equipo de ok2app. No publicada en Google Play.

---

## Características del MVP

- Conexión autenticada con la API Laravel (token por dispositivo)
- Polling automático de mensajes pendientes (cada 60 segundos)
- Envío SMS nativo mediante la SIM del dispositivo
- Rate limiting configurable (máximo N mensajes por minuto)
- Estados claros: `pending → claimed → sending → sent/failed`
- Log local de últimos 100 envíos (Room)
- Pausa/reanudación del procesamiento sin apagar el servicio
- Autostart en reinicio del dispositivo
- Notificación persistente con estado en tiempo real
- Watchdog WorkManager (cada 15 min) para garantizar continuidad

---

## Arquitectura

```
┌─────────────────────────────────────────────────────┐
│                  Laravel API                        │
│  sms_gateways · sms_messages · sms_delivery_logs   │
└──────────────┬──────────────────────────────────────┘
               │  HTTP (Bearer token)
               │  Retrofit 2.11 + OkHttp 4.12
               ▼
┌─────────────────────────────────────────────────────┐
│              Android SMS Gateway                    │
│                                                     │
│  GatewayRepository                                  │
│    ├── GET  /messages/pending  (fetch cola)         │
│    ├── POST /messages/{id}/claim                    │
│    ├── SmsSender (SmsManager nativo)                │
│    └── POST /messages/{id}/status (report)          │
│                                                     │
│  SmsForegroundService  ←→  WorkManager watchdog     │
│  Room (log local)  ·  DataStore (config)            │
└─────────────────────────────────────────────────────┘
```

### Stack técnico

| Capa | Tecnología |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navegación | Navigation Compose 2.9 |
| Estado | ViewModel + StateFlow |
| Red | Retrofit 2.11 + OkHttp 4.12 + Gson |
| Base de datos local | Room 2.7 (KSP) |
| Preferencias | DataStore Preferences 1.1 |
| Tareas en background | ForegroundService + WorkManager 2.10 |
| Coroutines | Kotlinx Coroutines 1.10 |
| Build | AGP 9.2.1 · Kotlin 2.1.10 · KSP 2.1.10-1.0.29 |

---

## Requisitos

- Android 7.0+ (API 24) o superior
- Tarjeta SIM activa con plan SMS
- Conectividad a internet (para la API)
- Permiso `SEND_SMS` (se solicita al iniciar la app)

---

## Build e instalación

### Requisitos de entorno

- Android Studio Meerkat o superior
- JDK 11+
- Dispositivo físico Android (el emulador no puede enviar SMS reales)

### Compilar APK de debug

```bash
./gradlew assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

### Instalar en dispositivo por ADB

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

O transferir el APK al dispositivo y abrirlo (habilitar "Instalar desde fuentes desconocidas" en Ajustes).

---

## Configuración inicial

1. Abrir la app → pantalla **Configurar Gateway**
2. Ingresar la **URL base** del servidor Laravel (`https://api.tudominio.com`)
3. Ingresar el **API Token** del dispositivo (generado en el panel admin del SaaS)
4. Configurar el **máximo de mensajes por minuto** (1–20, default 5)
5. Tocar **Conectar** → la app registra el dispositivo y navega al Dashboard

---

## Pantallas

### Dashboard

```
┌─────────────────────────────────────┐
│ ● CONECTADO    Última sync: 14:32  │
├─────────────────────────────────────┤
│  Pendientes: 3  Enviados: 47  Fallas: 1 │
├─────────────────────────────────────┤
│  Gateway activo  ●────────────────○ │ ← Toggle pausar
│  [ Sincronizar ahora ]              │
└─────────────────────────────────────┘
```

### Historial

Lista de los últimos 100 mensajes procesados con destinatario, estado (badge de color) y timestamp.

---

## Integración con Laravel

Ver especificación completa en [`docs/API_CONTRACT.md`](docs/API_CONTRACT.md).

### Endpoints requeridos

```
POST /api/sms-gateway/register
POST /api/sms-gateway/heartbeat
GET  /api/sms-gateway/messages/pending
POST /api/sms-gateway/messages/{id}/claim
POST /api/sms-gateway/messages/{id}/status
```

### Tablas sugeridas

```sql
sms_gateways       -- dispositivos registrados (gateway_id, token, device_name, last_seen)
sms_messages       -- cola de mensajes (id, recipient, content, status, priority, gateway_id)
sms_delivery_logs  -- historial de intentos (message_id, status, error_message, processed_at)
```

### Estados de mensaje

```
pending → claimed → sending → sent
                            ↘ failed
         cancelled (en cualquier punto antes de sending)
```

---

## Seguridad

- El token de API se almacena en DataStore (cifrado en disco en Android 6+)
- Cada dispositivo tiene su propio token; rotar si el dispositivo se pierde
- El servicio sólo procesa mensajes que el gateway reclamó explícitamente (estado `claimed`)
- El rate limiting previene envíos masivos accidentales

---

## Advertencia — Google Play

> Esta app usa el permiso `SEND_SMS` que está clasificado como **permiso de alto riesgo** por Google Play.  
> Su distribución en la Play Store requiere justificación detallada y revisión manual.  
> **Esta app se distribuye exclusivamente por APK sideload** para dispositivos de uso interno del equipo ok2app.

---

## Estructura del proyecto

```
SMS_Broadcaster/
├── app/
│   ├── schemas/                    ← Room DB schemas exportados (versión de BD)
│   ├── src/main/java/com/ok2app/sms/
│   │   ├── data/                   ← local (Room, DataStore) + remote (Retrofit)
│   │   ├── domain/model/           ← GatewayConfig, SmsMessage
│   │   ├── receiver/               ← BootReceiver
│   │   ├── service/                ← SmsForegroundService, SmsSender
│   │   ├── worker/                 ← GatewayWatchdogWorker, RateLimiter
│   │   └── ui/                     ← screens (setup, dashboard, history) + navigation
│   └── src/main/AndroidManifest.xml
├── docs/
│   └── API_CONTRACT.md             ← Contrato de API para el backend Laravel
├── CLAUDE.md                       ← Contexto para asistente IA
└── README.md                       ← Este archivo
```

---

## Desarrollo futuro (post-MVP)

- Selección de SIM en dispositivos dual-SIM
- Intervalo de polling configurable desde UI
- Reintentos automáticos con exponential backoff
- Soporte para mensajes multimedia (MMS)
- Panel de administración web para gestionar gateways registrados
- Tests unitarios e instrumentados
