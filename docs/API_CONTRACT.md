# Contrato de API — SMS Gateway

Especificación de los endpoints que el servidor Laravel debe implementar para la integración con la app Android SMS Gateway.

**Base URL:** configurable por el operador (ej. `https://api.tudominio.com`)  
**Auth:** todos los endpoints requieren `Authorization: Bearer {token}` en el header.  
**Content-Type:** `application/json`

---

## Flujo general

```
[App Android]                          [Laravel API]
     │                                      │
     │── POST /register ──────────────────► │  Registra dispositivo
     │◄─ { gateway_id } ───────────────────│
     │                                      │
     │── GET /messages/pending ────────────► │  Obtiene mensajes para enviar
     │◄─ { data: [{id, recipient, ...}] } ─│
     │                                      │
     │── POST /messages/{id}/claim ────────► │  Reclama mensaje (evita duplicados)
     │◄─ { id, recipient, content } ───────│
     │                                      │
     │  [envía SMS por SIM]                 │
     │                                      │
     │── POST /messages/{id}/status ───────► │  Reporta resultado
     │   { status: "sent" }                 │
     │                                      │
     │── POST /heartbeat ──────────────────► │  Señal de vida (cada 15 min)
```

---

## Endpoints

### 1. Registrar gateway

Registra el dispositivo Android como gateway. Se llama una vez en el setup inicial.

```
POST /api/sms-gateway/register
```

**Request body:**
```json
{
  "device_name": "SM-A546B",
  "device_model": "Samsung SM-A546B",
  "app_version": "1.0"
}
```

**Response 200:**
```json
{
  "gateway_id": "gw_01HXYZ..."
}
```

**Comportamiento sugerido en Laravel:**
- Crear o actualizar registro en `sms_gateways` usando el token como identificador.
- Si ya existe un gateway con ese token, retornar el mismo `gateway_id`.
- Guardar `device_name`, `device_model`, `app_version`, `last_registered_at`.

---

### 2. Heartbeat

Señal de vida del gateway. La app lo envía cada ~15 minutos vía WorkManager.

```
POST /api/sms-gateway/heartbeat
```

**Request body:**
```json
{
  "gateway_id": "gw_01HXYZ...",
  "status": "active"
}
```

**Response 200:** vacío o `{ "ok": true }`

**Comportamiento sugerido:** actualizar `last_seen_at` en `sms_gateways`.

---

### 3. Obtener mensajes pendientes

La app consulta este endpoint cada 60 segundos. Retorna mensajes en estado `pending` que pueden ser procesados por este gateway.

```
GET /api/sms-gateway/messages/pending
```

**Response 200:**
```json
{
  "data": [
    {
      "id": 42,
      "recipient": "+5491112345678",
      "content": "Hola! Tu turno es mañana a las 10:00.",
      "priority": 0
    },
    {
      "id": 43,
      "recipient": "+5491198765432",
      "content": "Recordatorio: factura vence el 30/06.",
      "priority": 1
    }
  ]
}
```

**Notas:**
- `priority` es un entero (mayor = más urgente). La app los procesa en el orden recibido.
- Retornar como máximo los primeros N mensajes (sugerido: 10) para evitar sobrecarga.
- Sólo retornar mensajes en estado `pending` (no `claimed` ni otros).
- Filtrar por `gateway_id` si se asignan mensajes a gateways específicos, o retornar cualquier pendiente si el gateway es compartido.
- Si no hay mensajes, retornar `{ "data": [] }`.

---

### 4. Reclamar mensaje

La app "reclama" un mensaje antes de enviarlo para evitar que dos gateways procesen el mismo mensaje simultáneamente.

```
POST /api/sms-gateway/messages/{id}/claim
```

**Response 200:** el mensaje reclamado
```json
{
  "id": 42,
  "recipient": "+5491112345678",
  "content": "Hola! Tu turno es mañana a las 10:00.",
  "priority": 0
}
```

**Response 409 (Conflict):** si el mensaje ya fue reclamado por otro gateway
```json
{
  "error": "Message already claimed"
}
```

**Comportamiento sugerido en Laravel:**
- Usar una transacción con lock para la actualización atómica.
- Cambiar estado de `pending` → `claimed`.
- Guardar `claimed_by` (gateway_id) y `claimed_at`.
- Si ya está en estado `claimed` o posterior, retornar 409.

---

### 5. Actualizar estado de mensaje

La app reporta el resultado del envío SMS.

```
POST /api/sms-gateway/messages/{id}/status
```

**Request body — envío exitoso:**
```json
{
  "status": "sent"
}
```

**Request body — envío fallido:**
```json
{
  "status": "failed",
  "error_message": "SMS rechazado por el modem (código 3)"
}
```

**Response 200:** vacío o `{ "ok": true }`

**Comportamiento sugerido en Laravel:**
- Actualizar `status` en `sms_messages`.
- Guardar registro en `sms_delivery_logs` con el resultado.
- Para `status=failed`, considerar si reencolar el mensaje (con límite de reintentos).

---

## Estados de mensaje

```
                    ┌──────────────────────────┐
                    ▼                          │ (reencolar si failed < max_retries)
pending ──► claimed ──► sending ──► sent       │
                              └──► failed ─────┘
            │
            └──► cancelled (cancelado por el administrador)
```

| Estado | Descripción |
|---|---|
| `pending` | En cola, esperando ser procesado |
| `claimed` | Reclamado por un gateway, en proceso |
| `sending` | SMS enviándose (estado intermedio en el gateway) |
| `sent` | SMS confirmado como enviado por el modem |
| `failed` | El envío falló (permiso, señal, error API, timeout) |
| `cancelled` | Cancelado manualmente por el administrador |

---

## Esquema de tablas sugerido (Laravel)

### `sms_gateways`

```php
Schema::create('sms_gateways', function (Blueprint $table) {
    $table->id();
    $table->string('gateway_id')->unique();   // ID público (prefijo gw_)
    $table->string('token', 80)->unique();    // Bearer token de autenticación
    $table->string('device_name')->nullable();
    $table->string('device_model')->nullable();
    $table->string('app_version')->nullable();
    $table->boolean('is_active')->default(true);
    $table->timestamp('last_seen_at')->nullable();
    $table->timestamps();
});
```

### `sms_messages`

```php
Schema::create('sms_messages', function (Blueprint $table) {
    $table->id();
    $table->string('recipient');              // Número destino E.164 (+549...)
    $table->text('content');
    $table->string('status', 20)->default('pending');  // pending|claimed|sending|sent|failed|cancelled
    $table->unsignedTinyInteger('priority')->default(0);
    $table->unsignedTinyInteger('retry_count')->default(0);
    $table->unsignedTinyInteger('max_retries')->default(3);
    $table->foreignId('gateway_id')->nullable()->constrained('sms_gateways')->nullOnDelete();
    $table->timestamp('claimed_at')->nullable();
    $table->timestamp('sent_at')->nullable();
    $table->timestamps();
});
```

### `sms_delivery_logs`

```php
Schema::create('sms_delivery_logs', function (Blueprint $table) {
    $table->id();
    $table->foreignId('sms_message_id')->constrained()->cascadeOnDelete();
    $table->foreignId('gateway_id')->nullable()->constrained('sms_gateways')->nullOnDelete();
    $table->string('status', 20);
    $table->text('error_message')->nullable();
    $table->timestamp('processed_at');
    $table->timestamps();
});
```

---

## Autenticación del gateway

Cada dispositivo Android tiene su propio token (no el token de usuario). El token se genera desde el panel de administración y se carga manualmente en la app durante el setup.

**Header requerido en todos los requests:**
```
Authorization: Bearer {token}
```

**Middleware de Laravel sugerido:**
```php
// app/Http/Middleware/AuthenticateGateway.php
$token = $request->bearerToken();
$gateway = SmsGateway::where('token', hash('sha256', $token))
                     ->where('is_active', true)
                     ->firstOrFail();
$request->merge(['gateway' => $gateway]);
```

> Guardar el hash SHA-256 del token en la base de datos, no el token en claro.

---

## Manejo de errores esperado por la app

| Código HTTP | Interpretación en la app |
|---|---|
| 200 | Éxito |
| 401 | Token inválido → mostrar error en dashboard, no reintentar |
| 404 | Mensaje no existe → saltar al siguiente |
| 409 | Mensaje ya reclamado → saltar al siguiente |
| 422 | Error de validación → loguear y continuar |
| 5xx | Error de servidor → PollResult.Error, mostrar en dashboard |
| Timeout | Sin conexión → PollResult.Skipped |

---

## Notas de implementación

- **Idempotencia en `/claim`:** usar `update ... where status = 'pending'` en una transacción para garantizar atomicidad. Retornar 409 si no se actualizó ninguna fila.
- **Rate limiting server-side:** no bloquear solicitudes frecuentes del gateway; el rate limiting ya lo maneja la app. Pero sí implementar throttling a nivel de IP si hay riesgo de abuso.
- **Orden de mensajes:** el endpoint `/pending` debería ordenar por `priority DESC, created_at ASC` para que los más urgentes salgan primero.
- **Timeouts de claim:** si un mensaje queda en estado `claimed` por más de X minutos sin pasar a `sent/failed`, considerar reponerlo a `pending` (via un job de Laravel).
