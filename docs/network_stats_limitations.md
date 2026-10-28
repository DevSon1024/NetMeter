# NetworkStatsManager Limitations & Known Discrepancies

This document outlines the architectural characteristics, system-level accounting behaviors, and known discrepancies when using Android's native `android.app.usage.NetworkStatsManager` within NetMeter.

## 1. Native API Architecture

NetMeter relies on `android.app.usage.NetworkStatsManager` querying the underlying Linux eBPF / `xt_qtaguid` network traffic accounting subsystem.

- **App-Level Traffic (`querySummary`):** Aggregates traffic per UID over a specified timestamp range.
- **Device-Level Traffic (`querySummaryForDevice`):** Measures total network interface data per transport type (`TYPE_WIFI`, `TYPE_MOBILE`).
- **Granular Detail Buckets (`queryDetails` / `queryDetailsForUid`):** Provides time-sliced buckets for historical analytics.

## 2. Known Discrepancies vs System & Carrier Counters

### 2.1 Kernel Flush and Persistence Latency
- **Behavior:** The Linux kernel buffers network packets in memory and flushes accounting data to disk/eBPF maps on timer intervals (typically every few minutes) or upon system events (e.g. screen sleep, interface transition).
- **Impact:** Live real-time throughput will momentarily diverge from `NetworkStatsManager` historical totals by a few megabytes until the kernel flushes statistics.

### 2.2 Billing Cycle Alignment
- **Behavior:** Carrier data meters and Android Settings "Data Warning & Limit" counters align to custom billing cycle reset dates (e.g., 18th of every month), whereas calendar analytics query midnight-to-midnight intervals.
- **Resolution:** Explicit time boundaries (`startTimeMillis` to `endTimeMillis`) are enforced in the domain layer.

### 2.3 Virtual Private Networks (VPN) Dual Accounting
- **Behavior:** When an active VPN service is running, network packets traverse both the virtual tunnel interface (`tun0`) and the underlying hardware transport interface (`wlan0` or `rmnet0`).
- **Impact:** NetworkStats attributes physical network traffic to the VPN provider app UID or records transport-level interface bytes. NetMeter queries hardware transports to avoid artificial duplication.

### 2.4 Mobile Hotspot & Tethering
- **Behavior:** When the device operates as a portable Wi-Fi hotspot or USB tether, forwarded data packets do not originate from an installed application UID.
- **Attribution:** The Android OS classifies hotspot traffic under special system constant `UID_TETHERING` (`-5`), mapped cleanly in NetMeter to "Tethering & Hotspot".

### 2.5 Uninstalled Applications
- **Behavior:** When an app is uninstalled within the query time window, its UID is de-registered from `PackageManager`.
- **Attribution:** Android groups past data for removed applications under `UID_REMOVED` (`-4`), mapped in NetMeter to "Removed Apps".

### 2.6 OS Daemons & Root Process Coalescing
- **Behavior:** Low-level kernel sockets and system daemons run under UID 0 (`root`) or UID 1000 (`Process.SYSTEM_UID`).
- **Attribution:** Public Android APIs do not permit inspecting internal kernel socket descriptors; these are accurately aggregated under "Android OS (Kernel)" and "Android System".

## 3. Security & Modern Android Constraints

### 3.1 Subscriber ID Access
- Accessing `subscriberId` (IMSI) requires the privileged permission `READ_PRIVILEGED_PHONE_STATE` (carrier/system apps only) or `READ_PHONE_STATE`.
- Starting with Android 10 (API 29), passing `null` as the `subscriberId` is the officially supported pattern for modern Android applications. It safely aggregates mobile data across all SIM slots without requesting intrusive telephony permissions.

### 3.2 TrafficStats Restrictions
- Calling `TrafficStats.getUidRxBytes(uid)` or `TrafficStats.getUidTxBytes(uid)` for other application UIDs was blocked in Android 10 (API 29) and returns `TrafficStats.UNSUPPORTED` (`-1`). NetMeter strictly uses `NetworkStatsManager` and never attempts unauthorized `TrafficStats` calls.

### 3.3 Thread Isolation & Resource Management
- All `NetworkStatsManager` binder queries are offloaded to `Dispatchers.IO` to ensure main thread responsiveness.
- Every `NetworkStats` cursor/binder reference is closed reliably via Kotlin's `.use { ... }` block to prevent native resource leaks.
