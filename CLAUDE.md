## Project Overview

TreadmillBridge - Android BLE bridge for fitness equipment:
* Connects to FTMS treadmills and HR monitors, receives real-time metrics
* Re-broadcasts via local GATT server to other devices (e.g., smartwatches)
* Full treadmill control with safety-first design

**Package**: `com.thirdwave.treadmillbridge`

## Architecture

**MVVM with Kotlin Flow**: UI (Compose) → ViewModel (StateFlow) → Repository → DataSource (BLE callbacks→Flow) → BLE Managers (Nordic)

**Key patterns**: StateFlow, Hilt DI, Repository pattern, Sealed classes (ConnectionState, TreadmillControlState, TreadmillRunningState), Coroutines

### Structure

```
data/
├── model/          TreadmillMetrics, HrMonitorMetrics, ConnectionState (sealed),
│                   TreadmillControlState (sealed), TreadmillRunningState (sealed),
│                   ControlTargets, DiscoveryState, HrDiscoveryState
├── repository/     TreadmillRepository, HrMonitorRepository
└── source/         BluetoothDataSource, TreadmillBleManager, HrMonitorBleManager,
                    GattServerManager
ui/
├── state/          TreadmillUiState (combined state)
├── viewmodel/      TreadmillViewModel (combines flows, heartbeat management)
├── screens/        DashboardScreen, DevicesScreen, ControlScreen
└── components/     StatusPanel, DeviceListItem
ble/                FTMSData, FTMSControlPoint, FTMSMachineStatus, FTMSFeatureData,
                    HeartRateData, BatteryData, SpeedRange, InclineRange
```

## BLE Implementation

### Central (Client) - Nordic BLE Library

**FTMS Treadmill Service (`0x1826`)**:

| Characteristic | UUID | Usage |
|----------------|------|-------|
| Treadmill Data | `0x2ACD` | Notification subscription - real-time speed, incline, distance |
| Control Point | `0x2AD9` | Write with indication - commands (request control, start, stop, set speed/incline) |
| Machine Status | `0x2ADA` | Notification subscription - state changes (started, stopped, paused, control lost) |
| Feature | `0x2ACC` | Read once on connect - supported features bitmap |
| Speed Range | `0x2AD4` | Read once on connect - min/max/step values |
| Incline Range | `0x2AD5` | Read once on connect - min/max/step values |

**HR Monitor Service (`0x180D`)**:

| Characteristic | UUID | Usage |
|----------------|------|-------|
| HR Measurement | `0x2A37` | Notification subscription - heart rate, RR intervals |
| Battery Level | `0x2A19` | Read once + notification - battery percentage |

**Connection**: Auto-retry (3x, 100ms delay), Nordic BLE request queuing for serial operations.

### Peripheral (Server) - Native Android GATT

Hosts FTMS service (`0x1826`), broadcasts Treadmill Data (`0x2ACD`) to connected clients (e.g., smartwatches).

## Devices Screen

Manages BLE device discovery and connections:

- **Treadmill Section**: Scans for FTMS service (`0x1826`), displays discovered devices with signal strength, allows connect/disconnect
- **HR Monitor Section**: Scans for HR service (`0x180D`), concurrent scanning supported
- **GATT Server Toggle**: Starts/stops the peripheral server for re-broadcasting metrics
- **Connection State**: Shows Disconnected/Connecting/Connected status per device

## Control Screen

Implements safety-first treadmill control with state machine driven by Machine Status notifications.

### Safety Rules
1. **Never send speed unless Started** - Speed commands only after Machine Status `0x02` received
2. **Never send speed after Pause** - Heartbeat stops immediately
3. **Never auto-resume** - Explicit user Play press required
4. **Never auto-restart after Stop** - Belt stays stopped
5. **Heartbeat only when running** - 3-second interval, only if speed > 0

### Control Flow

```
                    [Request Control]
                           │
                           ▼
                   Control Point 0x00
                           │
                           ▼
              Success Response (0x80 + 0x00 + 0x01)
                           │
                           ▼
              ┌────────────┴────────────┐
              │    CONTROLLING STATE    │
              │  "App is controlling"   │
              │  Play/Pause/Stop enabled│
              └────────────┬────────────┘
                           │
                   [User presses Play]
                           │
                           ▼
               Control Point: startOrResume()
                           │
                           ▼
              Machine Status 0x02 (StartedOrResumed)
                           │
                           ▼
    ┌──────────────────────┴──────────────────────┐
    │              RUNNING STATE                   │
    │  Play button: GREEN                          │
    │  Sliders: ENABLED                            │
    │  Heartbeat: ACTIVE (setTargetSpeed every 3s)│
    └──────────────────────┬──────────────────────┘
                           │
       ┌───────────────────┼───────────────────┐
       ▼                   ▼                   ▼
   [Pause]             [Stop]          [Emergency/Error]
       │                   │                   │
       ▼                   ▼                   ▼
  ┌─────────┐        ┌─────────┐        Machine Status
  │ PAUSED  │        │ STOPPED │        0x01/0x10/0x12
  │ ORANGE  │        │  RED    │              │
  │ sliders │        │ sliders │              ▼
  │ retain  │        │ reset=0 │        Control Revoked
  └─────────┘        └─────────┘
```

### Button State Logic
| Running State | Play | Pause | Stop | Sliders |
|---------------|------|-------|------|---------|
| Stopped | normal | normal | **RED** | disabled, values=0 |
| Running | **GREEN** | normal | normal | enabled |
| Paused | normal | **ORANGE** | normal | disabled, retain values |

## Data Flow

```
Treadmill → TreadmillBleManager → BluetoothDataSource → TreadmillRepository →
HR Monitor → HrMonitorBleManager → BluetoothDataSource → HrMonitorRepository →
                                   ↓
                         TreadmillViewModel (combines 10+ flows)
                                   ↓
                            TreadmillUiState
                                   ↓
                          UI (collectAsState)

Parallel: Metrics → GattServerManager → Connected clients
```

## Key Components

**BluetoothDataSource**: Converts BLE callbacks to StateFlows, tracks controlState and runningState from Machine Status/Control Point responses

**TreadmillViewModel**: Combines repository flows into TreadmillUiState, manages heartbeat coroutine with safety guards

**BLE Parsers** (`ble/`):
- `FTMSData`: Treadmill Data (0x2ACD) - flag-based optional fields
- `FTMSControlPoint`: Command builder + response parser (0x2AD9)
- `FTMSMachineStatus`: All opcodes 0x00-0x12 as sealed class hierarchy (0x2ADA)
- `FTMSFeatureData`: Machine features + target setting features bitmap (0x2ACC)
- `HeartRateData`: HR Measurement (0x2A37) - flag-based, RR intervals
- `BatteryData`: Battery Level (0x2A19)
