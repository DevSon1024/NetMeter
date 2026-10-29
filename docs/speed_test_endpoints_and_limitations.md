# NetMeter Speed Test Engine & Endpoint Documentation

## 1. Dedicated Engine Overview
The NetMeter Speed Test Engine is completely decoupled from the system `android.app.usage.NetworkStatsManager` data layer. It provides an on-demand, user-initiated HTTP throughput and latency measurement pipeline built on Kotlin Coroutines and standard HTTP streaming.

## 2. Configured Endpoints & Cloudflare Infrastructure
NetMeter avoids arbitrary or undocumented servers by utilizing Cloudflare's publicly documented speed test infrastructure:
- **Default Download URL:** `https://speed.cloudflare.com/__down?bytes=10485760` (10 MB bounded payload)
- **Default Upload URL:** `https://speed.cloudflare.com/__up` (HTTP POST chunked streaming)
- **Default Latency URL:** `https://speed.cloudflare.com/__down?bytes=0` (0 bytes HTTP HEAD/GET)

All URLs, limits, and timeouts are encapsulated in `SpeedTestConfig` and can be overridden programmatically.

## 3. Strict Payload Ceilings & Data Consumption Protection
To prevent runaway carrier data usage and user bill shock:
- **Download Payload Ceiling:** 10 MB (`DEFAULT_MAX_DOWNLOAD_BYTES`).
- **Upload Payload Ceiling:** 5 MB (`DEFAULT_MAX_UPLOAD_BYTES`).
- **Phase Execution Timeout:** 10 seconds per transfer phase (`DEFAULT_PHASE_TIME_LIMIT_MS`).
- **Overall Data Cap:** An entire test run transfers between 10 MB and 15 MB of data.
- **User-Initiated Execution:** Speed tests are never run in the background, on screen launch, or on a timer.

## 4. HTTP Round-Trip Latency vs. ICMP Ping
- **Application Layer (L7) vs Internet Layer (L3):**
  NetMeter explicitly reports **HTTP Latency** and **HTTP Jitter**, not "ICMP Ping".
- **Distinction:**
  ICMP ping measures raw IP echo response at Layer 3. HTTP latency measures the full application round-trip time, including TCP 3-way handshake, TLS 1.3 cryptographic session setup, and HTTP request/response header processing. Consequently, HTTP latency is naturally higher than low-level ICMP ping.
- **Statistical Jitter:**
  Calculated following the RFC 3550 standard specification:
  $$J = \frac{1}{N - 1} \sum_{i=2}^{N} |D_i - D_{i-1}|$$
  where $D_i$ represents the latency of sample $i$.

## 5. Technical Limitations
1. **Single-Stream Measurement:**
   NetMeter utilizes a single HTTP connection stream to minimize device resource consumption and battery drain. Multi-threaded gigabit ISP pipe saturation is outside the scope of this utility.
2. **CDN PoP Proximity:**
   Results reflect latency and throughput to the nearest Cloudflare Anycast CDN Point of Presence (PoP), which may differ from local ISP peering exchange nodes.
3. **Carrier Shaping & Bufferbloat:**
   Mobile carriers may apply rate shaping or quality-of-service rules to non-prioritized HTTP ports.
