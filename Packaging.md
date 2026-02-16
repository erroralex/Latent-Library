# 📦 Packaging Guide: Portable Desktop App

This guide details how to build **AI Toolbox** as a standalone, portable Windows executable (`.exe`) that requires **no installation** and has **no external dependencies** (Java and Node.js are bundled).

---

## 📋 Prerequisites

1.  **JDK 21 Installed:** Ensure you have a JDK 21 installed (e.g., BellSoft Liberica, Corretto, or Zulu).
2.  **Node.js Installed:** Required for building the frontend and Electron wrapper.
3.  **Administrator Privileges:** Required for creating symbolic links during the final build process.

---

## 🚀 Step-by-Step Build Process

### Phase 1: Build the Frontend

Compile the Vue.js frontend. The build configuration is set to automatically output files into the backend's resource folder.

1.  Open a terminal in the **`frontend`** directory:
    ```powershell
    cd frontend
    ```
2.  Install dependencies and build:
    ```powershell
    npm install
    npm run build
    ```
    *Success Check:* Ensure files were generated in `../backend/src/main/resources/static/`.

---

### Phase 2: Build the Backend JAR

Compile the Spring Boot backend into a single executable JAR.

1.  Navigate to the **`backend`** directory:
    ```powershell
    cd ../backend
    ```
2.  Run the Maven wrapper to build the JAR (skipping tests for speed):
    ```powershell
    .\mvnw.cmd clean package -DskipTests
    ```
    *Success Check:* Verify that `backend-0.0.1-SNAPSHOT.jar` exists in `backend/target/`.

---

### Phase 3: Create Custom Java Runtime (JRE)

Create a stripped-down, lightweight Java Runtime Environment to bundle with the app.

1.  Navigate to the **Project Root** (`AIToolbox-Web`).
2.  Identify your JDK path (e.g., `C:\Program Files\BellSoft\LibericaJDK-21`).
3.  Run the following `jlink` command in PowerShell:

    ```powershell
    # Adjust the path to match YOUR JDK installation
    & "C:\Program Files\BellSoft\LibericaJDK-21\bin\jlink.exe" `
        --module-path "C:\Program Files\BellSoft\LibericaJDK-21\jmods" `
        --add-modules java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.jfr,jdk.management,jdk.unsupported,jdk.crypto.ec `
        --output electron/runtime `
        --strip-debug `
        --no-man-pages `
        --no-header-files `
        --compress=2
    ```
    *Success Check:* Verify that the folder `electron/runtime/bin` exists and contains `java.exe`.

---

### Phase 4: Build the Portable Executable

Package everything (Frontend + Backend JAR + Custom JRE) into a single `.exe`.

**⚠️ Important:** You must run this step as **Administrator** to allow symbolic link creation.

1.  **Right-click** your Terminal/PowerShell icon and select **"Run as Administrator"**.
2.  Navigate to the **`electron`** directory:
    ```powershell
    cd C:\Path\To\AIToolbox-Web\electron
    ```
3.  Install dependencies and build:
    ```powershell
    npm install
    npm run dist
    ```

---

## 📤 Release

1.  **Locate the Output:**
    Go to `electron/dist/`. You will find:
    *   `AI Toolbox 1.0.0.exe` (Portable Executable)
    *   `win-unpacked/` (Unpacked folder version)

2.  **Zip & Share:**
    *   Right-click `AI Toolbox 1.0.0.exe` -> **Compress to ZIP file**.
    *   Name it `AI-Toolbox-Portable-v1.0.0.zip`.
    *   Upload this ZIP to GitHub Releases.

---

## 🛠️ Troubleshooting

*   **"Cannot create symbolic link":** Ensure you are running the Phase 4 commands in an **Administrator** terminal.
*   **"mvn not found":** Use `.\mvnw.cmd` instead of `mvn`.
*   **"jlink not found":** Verify your JDK path in Phase 3. You can find it by running `$env:JAVA_HOME` in PowerShell.
