## Project Overview

TreadmillBridge is an Android application that acts as a BLE (Bluetooth Low Energy) bridge for fitness equipment.
* connects to a treadmill implementing the FTMS (Fitness Machine Service) protocol
* connects to a HR monitor
* re-broadcasts the data via a local GATT server, allowing other devices (like smartwatches) to receive treadmill metrics.
* allows manual control of treadmill parameters like speed, incline
* allows dynamic treadmill speed adjustment based on HR from connected monitor (using PID algorithm)

**Package**: `com.thirdwave.treadmillbridge`

## Project Structure

```
app/src/main/java/com/thirdwave/treadmillbridge/
├── MainActivity.kt              - Main activity with Compose UI
├── BluetoothBridgeManager.kt    - BLE orchestrator (Nordic + native)
├── ftms/
│   └── FTMSData.kt             - FTMS parser/builder (full spec)
├── utils/
│   └── UnitConversions.kt      - Reusable conversion utilities
└── ui/theme/
    └── Theme.kt                - Material3 theme configuration
```

**Key modules:**
- **`ftms/`**: FTMS protocol implementation, reusable across projects
- **`utils/`**: Common utilities (conversions, formatting), UI-independent
- **Root package**: Android-specific BLE and UI code

## Architecture

### BLE Dual-Role Implementation

The app operates in **both BLE roles simultaneously** with different technology stacks:

1. **Central (Client) Role**: Scans for and connects to FTMS-compatible treadmills **using Nordic BLE library**
   - Scans for devices advertising FTMS service UUID `00001826-0000-1000-8000-00805f9b34fb` (native scanner)
   - Connects using Nordic BLE `BleManager` for robust connection handling
   - Automatic retries (3 attempts, 100ms delay) on connection failures
   - Request queuing ensures operations execute in correct order
   - Subscribes to Treadmill Data characteristic `00002AD1-0000-1000-8000-00805f9b34fb`
   - Parses incoming FTMS data packets (speed, incline, cadence)

2. **Peripheral (Server) Role**: Acts as a GATT server exposing FTMS data **using Native Android APIs**
   - Hosts a local `BluetoothGattServer` with the same FTMS service
   - Allows external devices (e.g., smartwatches) to connect and subscribe
   - Notifies connected clients when treadmill data updates
   - Native implementation provides better control over server lifecycle

### Core Components

**`BluetoothBridgeManager`** (`BluetoothBridgeManager.kt`)
- Central orchestrator for all BLE operations
- Manages both Nordic BLE client (to treadmill) and native GATT server (to clients)
- Maintains shared state: `speedKph`, `inclinePercent`, `cadence`
- Hybrid approach: Nordic for robust treadmill connection, native Android for GATT server
- Uses `FTMSTreadmillData` for parsing/building FTMS payloads

**`TreadmillBleManager`** (`BluetoothBridgeManager.kt`)
- Inner class extending Nordic's `BleManager`
- Handles service discovery and validation (`isRequiredServiceSupported`)
- Manages characteristic notifications (`initialize`, `setNotificationCallback`)
- Provides connection lifecycle callbacks via `ConnectionObserver`
- Automatic cleanup on disconnect (`onServicesInvalidated`)

**`FTMSTreadmillData`** (`ftms/FTMSData.kt`)
- Complete FTMS Treadmill Data characteristic parser/builder
- Implements full GATT FTMS v1.0 specification
- Supports all optional fields: average speed, distance, elevation, pace, energy, heart rate, time, power
- Provides `parse()` for decoding and `build()` for encoding FTMS data
- Robust error handling with detailed logging

**`UnitConversions`** (`utils/UnitConversions.kt`)
- Reusable utility object for fitness-related conversions
- Speed ↔ Pace: `kmhToPaceMinPerKm()`, `paceMinPerKmToKmh()`, `speedToPaceString()`
- Distance: `kmToMiles()`, `milesToKm()`, `metersToFeet()`, `feetToMeters()`
- Speed: `kmhToMs()`, `msToKmh()`
- Formatting: `formatPace()`, `formatTime()`
- Available to all modules (UI, services, tests)

**`MainActivity`** (`MainActivity.kt`)
- Handles runtime permission requests for Android 12+ (BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE, ACCESS_FINE_LOCATION)
- Initializes `BluetoothBridgeManager` with manual GATT server control
- Uses Jetpack Compose for UI with Material3 adaptive navigation
- Displays treadmill metrics including pace (using `UnitConversions`)
- Status panel shows connection states for treadmill and GATT clients

### Data Flow

```
Treadmill (FTMS)
  → BLE Advertisement (FTMS service UUID)
  → App scans (native Android scanner)
  → User selects device from discovery list
  → Connects via Nordic BLE Manager (BluetoothBridgeManager.connectToDevice())
    ├─ Service discovery (TreadmillBleManager.isRequiredServiceSupported())
    ├─ Validates FTMS characteristic exists
    └─ Enables notifications (TreadmillBleManager.initialize())
  → Receives notifications with FTMS payload
  → Parses using FTMSTreadmillData.parse() (ftms/FTMSData.kt)
    ├─ Full FTMS v1.0 specification support
    ├─ Validates payload structure and field presence
    ├─ Parses all optional fields (speed, incline, distance, pace, energy, HR, time, power)
    ├─ Robust buffer bounds checking
    └─ Detailed logging for debugging
  → Updates BluetoothBridgeManager state (speedKph, inclinePercent)
  → Builds GATT server payload via FTMSTreadmillData.build()
  → Updates local GATT server characteristic (native Android)
  → Notifies connected clients (BluetoothBridgeManager.notifyLocalClients())
  → Smartwatch/other devices receive updated metrics
```

### FTMS Data Parsing

The app implements **full FTMS v1.0 Treadmill Data characteristic specification** via `FTMSTreadmillData` (`ftms/FTMSData.kt`):

**Parsing (`FTMSTreadmillData.parse()`)**:
- **Validation**: Checks for empty data and minimum payload length (4 bytes: flags + instantaneous speed)
- **Flags field** (uint16, little-endian) indicates which optional fields are present:
  - Bit 1: Average Speed (uint16, 0.01 km/h)
  - Bit 2: Total Distance (uint24, 1 meter)
  - Bit 3: Inclination & Ramp Angle (sint16, 0.1% and 0.1°)
  - Bit 4: Elevation Gain (uint16, 0.1 meter)
  - Bit 5: Instantaneous Pace (uint8, km/min)
  - Bit 6: Average Pace (uint8, km/min)
  - Bit 7: Expended Energy (total, per hour, per minute in kCal)
  - Bit 8: Heart Rate (uint8, bpm)
  - Bit 9: Metabolic Equivalent (uint8, 0.1 MET)
  - Bit 10: Elapsed Time (uint16, seconds)
  - Bit 11: Remaining Time (uint16, seconds)
  - Bit 12: Force on Belt & Power Output (sint16, N and W)
- **Always present**: Instantaneous Speed (uint16, 0.01 km/h) follows flags
- **Buffer bounds checking**: Validates remaining bytes before reading each field
- **Error handling**: Returns `null` on parse failure, logs detailed error information

**Building (`FTMSTreadmillData.build()`)**:
- Constructs proper FTMS payload from `FTMSTreadmillData` object
- Automatically sets flags based on which fields are present
- Encodes all fields in correct order per FTMS specification
- Used by GATT server to broadcast data to connected clients

**Integration**:
- `BluetoothBridgeManager.parseTreadmillData()` uses `FTMSTreadmillData.parse()` for incoming data
- `BluetoothBridgeManager.buildTreadmillDataPayload()` uses `FTMSTreadmillData.build()` for GATT server
- Decouples FTMS protocol handling from business logic

## Key Technical Constraints

- **Min SDK**: 35 (Android 15+) — limits device compatibility
- **BLE permissions**: Requires runtime permissions on Android 12+; permission flow handled in `MainActivity:onCreate()`
- **GATT server limitations**: Android's native GATT server behavior varies across OEM ROMs
- **Hybrid BLE stack**: Nordic library for treadmill connection, native Android for GATT server
- **Connection retry logic**: Nordic provides 3 automatic retries with 100ms delay
- **Not in version control**: This is not a git repository (no .git directory)

## Development Notes

### BLE Library Usage

**When to use Nordic BLE library:**
- Connecting to external BLE devices (treadmills, sensors, etc.)
- Need automatic retry logic and connection stability
- Require request queuing for sequential operations
- Want cleaner callback structure via `ConnectionObserver`

**When to use Native Android APIs:**
- Implementing GATT server functionality
- Device scanning (Nordic doesn't provide scanner improvements)
- Need fine-grained control over server lifecycle
- Working with descriptor write requests

### Permission Handling
Runtime permissions are requested via `ActivityResultLauncher` in `MainActivity`. Ensure all Bluetooth operations check `hasPermission()` helper before execution to avoid SecurityExceptions.

### Testing BLE Functionality
- **Physical device required**: BLE features don't work reliably on emulators
- **Target devices**: Must support Android 15+ (minSdk 35)
- To test: Run app, grant permissions, ensure real FTMS treadmill is powered on and advertising, verify connection in logs
- **Nordic BLE logs**: Set `getMinLogPriority()` to `Log.VERBOSE` for detailed connection debugging

### Connection Lifecycle
- **Scan**: User-initiated via "Connect Treadmill" button (manual scanning)
- **Device selection**: User selects device from discovered list
- **Connect**: `TreadmillBleManager.connect()` with retry(3, 100)
- **Service discovery**: Automatic via Nordic's `isRequiredServiceSupported()`
- **Notifications**: Enabled in `initialize()` callback
- **Disconnect**: `disconnectTreadmill()` resets treadmill values to 0, notifies GATT clients
- **Cleanup**: `close()` releases Nordic manager resources
- **Stop**: `stop()` handles full cleanup (scan, treadmill, GATT server)

### GATT Server Management
- **Manual control**: User explicitly starts/stops GATT server via UI buttons
- **State tracking**: `isGattServerRunning` property tracks server state
- **Client tracking**: Monitors connected GATT clients via callbacks
- **Lifecycle**: GATT server and treadmill connection are independent
- **Status display**: UI shows both treadmill connection and GATT server state

### Unit Conversions
Use `UnitConversions` object for all fitness-related conversions:
```kotlin
// Speed to pace
val paceString = UnitConversions.speedToPaceString(12.5f) // "4:48"
val paceMinPerKm = UnitConversions.kmhToPaceMinPerKm(12.5f) // 4.8f

// Distance conversions
val miles = UnitConversions.kmToMiles(10f) // 6.21371f
val feet = UnitConversions.metersToFeet(100f) // 328.084f

// Time formatting
val timeStr = UnitConversions.formatTime(3665) // "1:01:05"
```

### FTMS Specification Compliance
**Treadmill Data Characteristic (0x2ACD)**: ✅ **Fully implemented** via `FTMSTreadmillData`
- All optional fields parsed and built correctly
- Proper unit scaling and data types per FTMS v1.0 spec
- Complete flag bit handling
- Buffer validation and error handling

**Not yet implemented** (future enhancements):
- FTMS Control Point characteristic (0x2AD9) - for controlling treadmill
- FTMS Feature characteristic (0x2ACC) - for capability negotiation
- Machine Status characteristic (0x2ADA) - for equipment state
- Treadmill speed control based on HR (PID algorithm) - planned feature
- Connection to HR monitor - planned feature
- MVVM architecture refactor - planned improvement