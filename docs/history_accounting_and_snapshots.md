# NetMeter History Accounting & Snapshot Persistence Architecture

This document specifies the accounting model, boundary calculations, and caching architecture used by the NetMeter History feature.

## 1. Native Android NetworkStats Accounting Characteristics

NetMeter queries Android's native `android.app.usage.NetworkStatsManager` to retrieve device-level and transport-level traffic statistics. When analyzing historical data from the operating system, several fundamental characteristics apply:

- **Kernel / eBPF Flush Granularity:** The Linux kernel and Android eBPF network monitors aggregate packets at hardware interfaces and write summaries into `/data/system/netstats/` periodically (typically every 2 hours, during screen-off intervals, or upon network interface state changes).
- **No Sub-Second Midnight Precision:** Native system statistics buckets reflect hardware counter transitions. A system bucket that spans across midnight (e.g., 23:00 to 01:00) represents network activity recorded during that interval. NetMeter queries discrete calendar boundaries (`00:00:00.000` to `23:59:59.999`) using the device's local timezone, but users must understand that operating system buckets do not provide per-second or atomic midnight-boundary precision.
- **Hardware Availability Differences:** Wi-Fi only tablets will return zero or unsupported results for cellular data queries. NetMeter distinctly flags cellular metrics as `Unavailable` rather than attributing fabricated zeros.

## 2. Local Calendar & Timezone Resiliency

All historical aggregations are performed relative to the device's current local calendar and active `TimeZone.getDefault()`:
- **DST (Daylight Saving Time) Transitions:** Rather than assuming 24-hour fixed intervals (`24 * 3600 * 1000 ms`), calendar days are calculated using `Calendar.add(Calendar.DAY_OF_YEAR, 1)` anchored around local noon (`12:00:00`). This completely prevents 23-hour or 25-hour DST spring-forward and fall-back boundary skips.
- **Ongoing Today Accounting:** The current day (`Today`) is in a state of continuous tracking and is marked with `DailyDataStatus.PARTIAL`. Closed past days with detected traffic are marked `DailyDataStatus.COMPLETE`.

## 3. Data Source Attribution: Android Netstats vs. Local Snapshots

NetMeter distinctly differentiates where historical records originate:

| Data Source | Attribution Tag | Behavior & Storage |
| :--- | :--- | :--- |
| **Android System Netstats** | `ANDROID_SYSTEM` | Queried live from `NetworkStatsManager`. Always used for the ongoing day (`Today`) to ensure real-time accuracy. |
| **Local Snapshot Store** | `LOCAL_SNAPSHOT` | Stored in SQLite database (`netmeter_history.db`). Captures immutable daily totals once a day is closed. |

### Preventing Double-Counting and Overlapping Buckets
- Daily records are keyed strictly by unique ISO date (`YYYY-MM-DD`, e.g., `2026-10-27`).
- Queries never overlap date boundaries: each day begins at `00:00:00.000` and terminates at `23:59:59.999`.
- When loading historical ranges, closed past days are loaded from the snapshot store if present, or fetched from `NetworkStatsManager` and saved to the snapshot store.
- Ongoing day (`Today`) is never permanently committed as a closed snapshot until the calendar day has officially concluded.
