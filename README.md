# WebSync — Minecraft Server ↔ Website Synchronization Plugin

**Made by: [CodeR3836](https://github.com/CodeR3836)**

WebSync is a lightweight, secure Minecraft server plugin that synchronizes **live server and player data** from a Paper-based Minecraft server to an external website over authenticated HTTPS.

It is designed as a bridge between a Minecraft server and a website backend, allowing websites to display real-time information such as:

* 🟢 Server online/offline status
* 👥 Online player count
* 📊 Server TPS
* 👤 Player profiles and statistics
* ⛏️ Gameplay statistics
* 🎮 Java / Bedrock platform information
* 💰 Player economy data
* 🏷️ Player rank information

WebSync is designed to be **reliable, extensible, and server-friendly**. If the website becomes unavailable, the Minecraft server continues running normally while WebSync retries failed requests in the background.

---

## ✨ Features

### 🌐 Server Synchronization

WebSync periodically sends server heartbeat information to the website, including:

* Server online status
* Current online player count
* Maximum player count
* Server TPS
* Server identifier / slug
* Last-seen timestamp
* Server heartbeat information

The heartbeat interval is configurable.

---

### 👤 Player Synchronization

WebSync can synchronize information about players currently online, including:

* Minecraft username
* UUID
* Online status
* Platform
* XP
* Playtime
* Kills
* Deaths
* Blocks broken
* Blocks placed
* Economy balance
* Rank

Player synchronization can be triggered automatically or manually using:

```text
/websync sync
```

---

### 🔐 Secure Authentication

WebSync uses authenticated HTTPS requests to protect communication between the Minecraft server and website.

Authentication includes:

* API key authentication
* HMAC request signatures
* Timestamp-based request validation
* Shared signing secret
* HTTPS transport
* Request retry protection

Sensitive credentials are never intentionally written to server logs.

WebSync does **not** log:

* API keys
* Signing secrets
* Full HMAC signatures
* Complete synchronization payloads

---

### 🔄 Automatic Retry

Temporary website/network failures do not stop the Minecraft server.

When a request fails, WebSync can automatically retry using configurable backoff delays.

Example:

```text
Request failed
      ↓
Retry
      ↓
Retry with increased delay
      ↓
Maximum attempts reached
      ↓
Request abandoned
```

This allows the Minecraft server to continue operating independently from the website.

---

### ⚡ Batch Player Synchronization

Player updates can be batched within a configurable time window to reduce unnecessary HTTP requests.

Configuration:

```yaml
batch-window-millis: 2000
```

This is especially useful for servers with multiple players joining or leaving within a short period.

---

### 🧩 Extensible Bridge System

WebSync intentionally does not hard-code support for every economy, permission, or crossplay plugin.

Instead, it provides bridge interfaces for external integrations.

#### Economy Bridge

```text
EconomyBridge
```

Can be connected to an economy provider such as Vault or another economy plugin.

#### Rank Bridge

```text
RankBridge
```

Can be connected to a permission/rank system.

#### Platform Bridge

```text
PlatformBridge
```

Can be connected to Geyser/Floodgate to detect Java and Bedrock players.

This keeps WebSync lightweight and avoids unnecessary dependencies.

---

# 🏗️ Architecture

WebSync acts as a communication layer between the Minecraft server and a website backend.

```text
                    HTTPS
┌───────────────────────────────┐
│       Minecraft Server        │
│                               │
│          Paper 1.21.x         │
└───────────────┬───────────────┘
                │
                │
                ▼
       ┌─────────────────┐
       │  WebSync Plugin │
       │                 │
       │ • Heartbeat     │
       │ • Player Sync   │
       │ • Statistics    │
       │ • HMAC Auth     │
       │ • Retry System  │
       │ • Bridges       │
       └────────┬────────┘
                │
                │ Authenticated HTTPS
                ▼
       ┌──────────────────────┐
       │    Website Backend   │
       │                      │
       │ /api/websync/        │
       │   heartbeat          │
       │   players            │
       └──────────┬───────────┘
                  │
                  ▼
            Website / API
```

WebSync does **not** directly access the website database.

Instead:

```text
Minecraft
   ↓
WebSync
   ↓
HTTPS API
   ↓
Website Backend
   ↓
Database
```

This keeps the Minecraft plugin independent from the website's database implementation.

---

# 📡 API Endpoints

WebSync communicates with the website through authenticated API endpoints.

### Server Heartbeat

```http
POST /api/websync/heartbeat
```

Used to synchronize server status and server-level metrics.

---

### Player Synchronization

```http
POST /api/websync/players
```

Used to synchronize player information.

---

# 📋 Requirements

## Minecraft Server

* Paper or Paper-compatible server
* Minecraft **1.21.x**
* Java **21+**

WebSync primarily uses stable Bukkit APIs and Paper's TPS API.

The plugin should work on compatible Paper forks, although compatibility with every fork is not guaranteed.

### Spigot

Vanilla Spigot is **not officially supported** because WebSync uses:

```java
Bukkit.getTPS()
```

which is Paper-specific.

---

## Build Requirements

* Java 21+
* Gradle 8.x

A Gradle Wrapper is recommended for reproducible builds.

---

# 📦 Installation

### 1. Build the plugin

Using the Gradle Wrapper:

```bash
./gradlew build
```

Windows:

```powershell
.\gradlew.bat build
```

The resulting plugin will be generated at:

```text
build/libs/websync-1.0.0.jar
```

---

### 2. Install the plugin

Copy:

```text
websync-1.0.0.jar
```

into:

```text
plugins/
```

Example:

```text
Minecraft Server/
└── plugins/
    └── websync-1.0.0.jar
```

---

### 3. Start the server

Start the Minecraft server once.

WebSync will generate:

```text
plugins/WebSync/config.yml
```

---

### 4. Configure WebSync

Open:

```text
plugins/WebSync/config.yml
```

and configure the website URL and authentication credentials.

---

# ⚙️ Configuration

Default configuration:

```yaml
websync:
  enabled: true
  base-url: "https://your-domain.example"
  api-key: ""
  signing-secret: ""

heartbeat-interval-seconds: 30
fallback-sync-interval-seconds: 60
batch-window-millis: 2000

http:
  connect-timeout-seconds: 5
  read-timeout-seconds: 10

retry:
  max-attempts: 5
  base-delay-seconds: 1
  max-delay-seconds: 30

server-slug: "main"
```

---

## Configuration Options

| Option                           | Description                              |
| -------------------------------- | ---------------------------------------- |
| `websync.enabled`                | Enables or disables WebSync              |
| `websync.base-url`               | Base URL of the website                  |
| `websync.api-key`                | Shared API authentication key            |
| `websync.signing-secret`         | HMAC signing secret                      |
| `heartbeat-interval-seconds`     | Server heartbeat interval                |
| `fallback-sync-interval-seconds` | Fallback player synchronization interval |
| `batch-window-millis`            | Player update batching window            |
| `http.connect-timeout-seconds`   | HTTP connection timeout                  |
| `http.read-timeout-seconds`      | HTTP read timeout                        |
| `retry.max-attempts`             | Maximum request retry attempts           |
| `retry.base-delay-seconds`       | Initial retry delay                      |
| `retry.max-delay-seconds`        | Maximum retry delay                      |
| `server-slug`                    | Unique server identifier                 |

---

# 🔑 Authentication Setup

The website backend and Minecraft plugin must use the same credentials.

Website environment:

```env
WEBSYNC_API_KEY=your-secret-api-key
WEBSYNC_SIGNING_SECRET=your-secret-signing-key
```

Minecraft:

```yaml
websync:
  api-key: "your-secret-api-key"
  signing-secret: "your-secret-signing-key"
```

The values must match exactly.

### ⚠️ Never commit secrets

Do **not** put real credentials inside:

* GitHub repositories
* README files
* Screenshots
* Public configuration files
* Server logs
* Discord messages
* Issue reports

Generate strong random secrets instead.

For example:

```bash
openssl rand -hex 32
```

---

# 🆔 Server Slug

Each WebSync instance identifies itself using:

```yaml
server-slug: "main"
```

The slug must match the corresponding server entry in the website backend.

For a single-server installation, the default is:

```text
main
```

For multiple Minecraft servers, each server should have its own unique slug.

Example:

```text
survival
skyblock
lifesteal
practice
```

---

# 🛠️ Commands

WebSync provides administrative commands for monitoring and controlling synchronization.

### `/websync status`

Displays the current WebSync configuration/status.

Requires:

```text
websync.admin
```

---

### `/websync sync`

Immediately triggers a player synchronization.

Useful for testing or forcing an update.

Requires:

```text
websync.admin
```

---

### `/websync reload`

Reloads the WebSync configuration without requiring a full server restart.

Requires:

```text
websync.admin
```

---

# 🔐 Permissions

| Permission      | Description                            | Default  |
| --------------- | -------------------------------------- | -------- |
| `websync.admin` | Access WebSync administrative commands | Operator |

---

# 📊 Synchronized Player Data

WebSync can provide the following player information to the website:

| Data            | Supported |
| --------------- | --------- |
| UUID            | ✅         |
| Username        | ✅         |
| Online status   | ✅         |
| Platform        | ✅         |
| XP              | ✅         |
| Playtime        | ✅         |
| Kills           | ✅         |
| Deaths          | ✅         |
| Blocks broken   | ✅         |
| Blocks placed   | ✅         |
| Economy balance | 🔌 Bridge |
| Rank            | 🔌 Bridge |

The exact fields depend on the website API contract and the configured bridges.

---

# 🔌 External Integrations

## 💰 Economy

By default, the economy bridge returns:

```text
0
```

To integrate a real economy system, register an economy provider through:

```text
EconomyBridge.register(...)
```

This allows WebSync to remain independent from any particular economy plugin.

Vault-based integration can be implemented without modifying the core synchronization architecture.

---

## 🏷️ Rank

The default rank bridge returns:

```text
null
```

A permissions/rank plugin can register a provider through:

```text
RankBridge.register(...)
```

This allows the website to display the player's current rank.

---

## 🎮 Java / Bedrock Detection

The default platform implementation reports:

```text
JAVA
```

If the server uses Geyser/Floodgate, platform detection can be connected through:

```text
PlatformBridge.register(...)
```

This allows the website to distinguish between:

```text
JAVA
BEDROCK
```

---

# ⛏️ Blocks Placed Tracking

Minecraft/Bukkit does not provide a built-in cumulative statistic for blocks placed.

Therefore, WebSync maintains its own counter.

The data is stored in:

```text
plugins/WebSync/placed-blocks.yml
```

### Important

The counter only tracks blocks placed **after WebSync was installed**.

It cannot reconstruct historical block-placement data from before installation.

---

# 📈 Experience Tracking

WebSync uses:

```java
Player#getTotalExperience()
```

to obtain player experience.

This should be considered an approximation rather than a permanent lifetime XP statistic.

Minecraft XP can change due to gameplay events such as death and XP loss.

---

# ⚡ TPS

WebSync obtains server TPS using Paper's:

```java
Bukkit.getTPS()
```

This is a Paper-specific API.

If TPS information is unavailable, WebSync does not attempt to guess the value.

Instead, the heartbeat can omit TPS or provide:

```text
null
```

---

# 🔄 Reliability

WebSync is designed so that website failures do not interfere with normal Minecraft server operation.

For example:

```text
Website goes offline
        ↓
WebSync request fails
        ↓
Retry with backoff
        ↓
Minecraft server continues normally
        ↓
Website comes back online
        ↓
WebSync resumes synchronization
```

WebSync operates as a supplementary integration layer rather than a critical dependency of the Minecraft server.

---

# 🧪 Testing the Connection

After configuring WebSync:

### 1. Check server logs

You should see a configuration-loaded message similar to:

```text
[WebSync] Connected configuration loaded for https://your-domain.example
```

This only confirms that the configuration was loaded.

It does **not** prove that the website accepted the request.

---

### 2. Check WebSync status

Run:

```text
/websync status
```

---

### 3. Force player synchronization

Run:

```text
/websync sync
```

---

### 4. Check the website

Verify the website's player/server endpoints or dashboard.

For example:

```text
/api/players
/api/server
```

---

### 5. Wait for heartbeat

The server heartbeat normally runs according to:

```yaml
heartbeat-interval-seconds: 30
```

Therefore, allow at least one heartbeat interval for server information to update.

---

# 🐛 Troubleshooting

## Authentication Failed

Example:

```text
[WebSync] Authentication failed for ...
```

Possible causes:

* Incorrect API key
* Incorrect signing secret
* Whitespace in credentials
* Incorrect environment variable
* Website and plugin credentials do not match
* Server clock is incorrect

The website may reject requests with stale timestamps.

Make sure the server's system time is synchronized using NTP.

---

## HTTP 400

Example:

```text
[WebSync] Request to ... rejected (HTTP 400)
```

This usually means that the website rejected the request payload.

Possible causes include:

* Invalid UUID
* Invalid username
* Invalid field type
* Missing required field
* Website/plugin API contract mismatch

Check the website backend logs for the exact validation error.

---

## HTTP 503 / Network Failure

Example:

```text
[WebSync] Request to ... failed: HTTP 503
```

Possible causes:

* Website is offline
* Reverse proxy failure
* DNS issue
* Internet connectivity problem
* Website API unavailable

WebSync will retry according to the configured retry policy.

---

## Player Data Is Not Updating

Check:

1. WebSync is enabled.
2. `base-url` is correct.
3. API key matches the website.
4. Signing secret matches the website.
5. `server-slug` is correct.
6. Website API is online.
7. Server clock is correct.
8. `/websync sync` works.
9. Website backend logs contain no validation errors.

---

# ⚠️ Limitations

WebSync deliberately keeps certain functionality outside the core plugin.

### Economy

Default implementation returns:

```text
0
```

A real economy integration must be registered separately.

---

### Rank

Default implementation returns:

```text
null
```

A permission/rank integration must be registered separately.

---

### Bedrock Detection

Without a platform bridge, players are reported as:

```text
JAVA
```

Geyser/Floodgate integration requires an appropriate bridge.

---

### Historical Blocks Placed

Only blocks placed after WebSync installation are tracked.

Historical placement data cannot be reconstructed automatically.

---

### XP

`Player#getTotalExperience()` is not a guaranteed lifetime XP counter.

---

### TPS

TPS depends on Paper's API and may not be available on non-Paper servers.

---

### Website Dependency

WebSync requires a compatible website backend implementing the expected API endpoints.

The plugin does not provide a complete website or database backend by itself.

---

### API Compatibility

The Minecraft plugin and website backend must follow the same API contract.

If one side changes its request/response schema without updating the other side, synchronization may fail.

---

# 🧱 Build From Source

Clone the repository:

```bash
git clone https://github.com/CodeR3836/WEBSYNC-PLUGIN.git
cd WEBSYNC-PLUGIN
```

Build using Gradle:

```bash
./gradlew build
```

Windows:

```powershell
.\gradlew.bat build
```

The generated JAR will be located at:

```text
build/libs/websync-1.0.0.jar
```

---

# 📁 Project Structure

```text
WEBSYNC-PLUGIN/
│
├── src/
│   └── main/
│       ├── java/
│       └── resources/
│
├── gradle/
│   └── wrapper/
│
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
├── README.md
├── LICENSE
└── .gitignore
```

Generated directories such as:

```text
.gradle/
build/
```

should not be committed to the repository.

---

# 🗺️ Roadmap

Potential future improvements include:

* [ ] Native Vault economy integration
* [ ] Native Geyser/Floodgate platform detection
* [ ] More configurable player statistics
* [ ] Improved synchronization metrics
* [ ] Better multi-server support
* [ ] More detailed WebSync diagnostics
* [ ] Configuration validation improvements
* [ ] Automated integration tests
* [ ] GitHub Actions CI builds
* [ ] Published release artifacts
* [ ] Expanded API documentation

The roadmap is subject to change as the project evolves.

---

# 🤝 Contributing

Contributions, bug reports, and suggestions are welcome.

Before submitting a pull request:

1. Keep changes focused.
2. Avoid committing secrets.
3. Test against a supported Paper version.
4. Preserve API compatibility where possible.
5. Update documentation when behavior changes.

For bugs, please include:

* Minecraft version
* Paper version
* Java version
* WebSync version
* Relevant error messages
* Configuration with secrets removed

**Never include API keys or signing secrets in bug reports.**

---

# 📜 License

This project is distributed under the license included in the repository.

See:

```text
LICENSE
```

for the full license terms.

---

# 👨‍💻 Author

**CodeR3836**

GitHub:

https://github.com/CodeR3836

WebSync was created to provide a simple and secure way to connect Minecraft servers with modern web applications without tightly coupling the server to a specific website database or third-party plugin.

---

## ⭐ Support the Project

If WebSync is useful to you, consider:

⭐ Starring the repository
🐛 Reporting bugs
💡 Suggesting features
🔧 Contributing improvements

---

**WebSync — Connecting Minecraft servers to the web.** 🚀
