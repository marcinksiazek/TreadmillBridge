## Project Overview

TreadmillBridge - Android BLE bridge for fitness equipment:
* Connects to FTMS treadmills and HR monitors, receives real-time metrics
* Re-broadcasts via local GATT server to other devices (e.g., smartwatches)
* Future: PID-based speed control

**Package**: `com.thirdwave.treadmillbridge`

## Architecture

**MVVM with Kotlin Flow**: UI (Compose) → ViewModel (StateFlow) → Repository → DataSource (BLE callbacks→Flow) → BLE Managers (Nordic)

**Key patterns**: StateFlow, Hilt DI, Repository pattern, Sealed classes (ConnectionState, GattServerState), Coroutines

### Structure

```
data/
├── model/          TreadmillMetrics, HrMonitorMetrics, ConnectionState (sealed),
│                   GattServerState (sealed), DiscoveryState, HrDiscoveryState
├── repository/     TreadmillRepository, HrMonitorRepository
└── source/         BluetoothDataSource, TreadmillBleManager, HrMonitorBleManager,
                    GattServerManager
di/                 AppModule, BluetoothModule
ui/
├── state/          TreadmillUiState (combined state)
├── viewmodel/      TreadmillViewModel (combines 7 StateFlows)
├── screens/        DashboardScreen, DevicesScreen, ControlScreen
└── components/     StatusPanel, DeviceListItem
ble/                FTMSData (FTMS parser), HeartRateData (0x2A37), BatteryData (0x2A19)
utils/              UnitConversions
```

### BLE Implementation

**Central (Client) - Nordic BLE**:
- Treadmill: FTMS service `0x1826`, subscribes to `0x2AD1`
- HR Monitor: HR service `0x180D`, subscribes to `0x2A37` (HR) and `0x2A19` (Battery)
- Auto-retry (3x, 100ms), request queuing

**Peripheral (Server) - Native Android GATT**:
- Hosts FTMS service, broadcasts metrics to connected clients

### Data Flow

```
Treadmill → TreadmillBleManager → BluetoothDataSource → TreadmillRepository →
HR Monitor → HrMonitorBleManager → BluetoothDataSource → HrMonitorRepository →
                                   ↓
                         TreadmillViewModel (combines flows)
                                   ↓
                            TreadmillUiState
                                   ↓
                          UI (collectAsState)

Parallel: Metrics → GattServerManager → Connected clients
```

## Key Components

**BluetoothDataSource**: Converts BLE callbacks to StateFlows, manages TreadmillBleManager, HrMonitorBleManager, GattServerManager

**TreadmillViewModel**: Combines 7 StateFlows (treadmill metrics, connection, GATT server, discovery, HR metrics, HR connection, HR discovery) into single TreadmillUiState

**BLE Parsers** (`ble/`):
- FTMSData: FTMS v1.0 Treadmill Data (0x2ACD) - all optional fields
- HeartRateData: HR Measurement (0x2A37) - flag-based parsing, RR intervals
- BatteryData: Battery Level (0x2A19)

## Technical Constraints

- **Min SDK**: 35 (Android 15+)
- **Permissions**: BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE, ACCESS_FINE_LOCATION
- **Testing**: Physical device + FTMS treadmill/HR monitor required

## Implementation Status

**Implemented**:
- ✅ FTMS Treadmill Data (0x2ACD)
- ✅ HR Monitor integration (0x2A37, 0x2A19)
- ✅ Concurrent treadmill/HR scanning
- ✅ Real-time metrics display (Dashboard: Speed, Pace, Incline, Cadence, HR)

**Not implemented**:
- FTMS Control Point (0x2AD9), Feature (0x2ACC), Machine Status (0x2ADA)
- PID-based speed control
