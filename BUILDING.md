# Building Latent Library from Source

This document describes how to build a fully self-contained Latent Library executable from source. The output mirrors the official releases — a single binary with a bundled Java runtime that requires no system-wide Java installation.

---

## Prerequisites

Before you begin, ensure the following are installed and available on your `PATH`:

| Tool | Version | Notes |
|---|---|---|
| **JDK** | 21 (Temurin recommended) | Must include `jlink` — full JDK, not just a JRE |
| **Maven** | 3.9+ | For building the Spring Boot backend |
| **Node.js** | 20.x | For building the Vue frontend and Electron shell |
| **npm** | Bundled with Node 20 | Used for all frontend/Electron dependency management |

> **Temurin JDK 21** is recommended as it is what the official CI pipeline uses.
> Download it from [adoptium.net](https://adoptium.net).

---

## Project Structure

```
Latent-Library/
├── backend/        # Spring Boot 3.3 / Java 21 (Maven)
├── frontend/       # Vue 3 + Vite
└── electron/       # Electron 31 shell and packaging config
```

---

## Step 1 — Build the Backend

```bash
cd backend
mvn clean package -DskipTests
```

This produces `backend/target/backend.jar` — the self-contained Spring Boot fat JAR.

> Omitting `-DskipTests` will run the full test suite, which is recommended but takes longer.

---

## Step 2 — Build the Frontend

```bash
cd frontend
npm ci
npm run build
```

This compiles the Vue 3 app into static assets that Electron will serve locally.

---

## Step 3 — Install Electron Dependencies

```bash
cd electron
npm ci
```

---

## Step 4 — Prepare the Runtime Directory

Electron needs the backend JAR and a bundled JRE placed in a specific structure before packaging:

```bash
mkdir -p electron/runtime/app
mkdir -p electron/runtime/bin
cp backend/target/backend.jar electron/runtime/app/
```

---

## Step 5 — Bundle a Custom JRE with jlink

Rather than shipping a full JDK, `jlink` produces a minimal custom runtime (~60MB) containing only the modules the application needs. Run this from the **project root**:

```bash
$JAVA_HOME/bin/jlink \
  --add-modules java.se,jdk.unsupported,jdk.crypto.ec \
  --output electron/runtime/jre \
  --strip-debug \
  --no-man-pages \
  --no-header-files \
  --compress=2

cp -r electron/runtime/jre/* electron/runtime/
```

**On Windows** (Git Bash or PowerShell):
```bash
"$JAVA_HOME/bin/jlink" \
  --add-modules java.se,jdk.unsupported,jdk.crypto.ec \
  --output electron/runtime/jre \
  --strip-debug \
  --no-man-pages \
  --no-header-files \
  --compress=2

cp -r electron/runtime/jre/* electron/runtime/
```

> The three included module groups serve specific purposes:
> - `java.se` — the full standard Java SE API surface, prevents `ClassNotFoundException` from transitive dependencies
> - `jdk.unsupported` — required by several Spring Boot and Netty internals (`sun.misc.Unsafe`)
> - `jdk.crypto.ec` — elliptic curve cryptography support for TLS connections

---

## Step 6 — Package the Electron App

```bash
cd electron
npm run dist
```

`electron-builder` will produce a platform-native binary in `electron/dist/`:

| Platform | Output |
|---|---|
| Windows | `Latent Library Setup X.X.X.exe` |
| Linux | `Latent Library-X.X.X.AppImage` |
| macOS | `Latent Library-X.X.X.dmg` |

---

## macOS Note

Because the app is not signed with an Apple Developer Certificate, macOS will block it on first launch. To clear the quarantine attribute after moving the `.app` to your Applications folder:

```bash
sudo xattr -cr "/Applications/Latent Library.app"
```

---

## Full Build — Copy/Paste Summary

For convenience, the complete sequence from project root (Linux/macOS):

```bash
# 1. Backend
cd backend && mvn clean package -DskipTests && cd ..

# 2. Frontend
cd frontend && npm ci && npm run build && cd ..

# 3. Electron dependencies
cd electron && npm ci && cd ..

# 4. Runtime directory
mkdir -p electron/runtime/app electron/runtime/bin
cp backend/target/backend.jar electron/runtime/app/

# 5. Bundled JRE
$JAVA_HOME/bin/jlink \
  --add-modules java.se,jdk.unsupported,jdk.crypto.ec \
  --output electron/runtime/jre \
  --strip-debug --no-man-pages --no-header-files --compress=2
cp -r electron/runtime/jre/* electron/runtime/

# 6. Package
cd electron && npm run dist
```

---

## Troubleshooting

**`jlink` not found**
Ensure you have a full JDK 21 installed, not just a JRE. Verify with `$JAVA_HOME/bin/jlink --version`.

**`ClassNotFoundException` at runtime**
A dependency requires a module not included in the custom JRE. Re-run `jlink` and add the missing module to `--add-modules`.

**Electron packaging fails on Linux**
Some distributions require `fuse` for AppImage creation. Install with `sudo apt install fuse` or equivalent.

**Frontend assets not found by Electron**
Ensure `npm run build` completed successfully in the `frontend/` directory before running `npm run dist` in `electron/`.