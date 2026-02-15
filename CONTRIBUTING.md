# Contributing to AI Toolbox

First off, thank you for considering contributing to **AI Toolbox**! It's people like you that make the open-source community such a fantastic place to learn, inspire, and create.

This document outlines the architecture, development workflow, and guidelines for contributing to the project.

---

## 🏗️ Project Architecture

AI Toolbox is a hybrid desktop application. Because it requires high-performance file hashing and local database management, it avoids standard Node.js backends in favor of a bundled Java runtime.

The repository is split into three main modules:
* **/backend**: A Java 21 Spring Boot application. It handles SQLite (FTS5) interactions, background indexing via Virtual Threads, and ONNX AI inference.
* **/frontend**: A Vue 3 + PrimeVue Single Page Application (SPA).
* **/electron**: The desktop wrapper. It spawns the Java backend, performs a secure port/token handshake, and opens a chromeless browser window.

---

## ⚙️ Development Environment Setup

To build and run this project locally, you will need the following installed on your system:

1. **Java Development Kit (JDK) 21**: Required for compiling the backend and running `jpackage`.
2. **Node.js (v22+) & npm**: Required for building the frontend and running Electron.
3. **Maven**: You can use your global Maven installation or the included `mvnw` wrapper.

---

## 🚀 Running the App Locally

Because the Electron app dynamically spawns the Spring Boot backend JAR, the easiest way to run the full application in "Dev Mode" is to build the backend JAR first, then start Electron.

### 1. Build the Backend (and Frontend)

The Maven `pom.xml` is configured to automatically install Node/npm, build the Vue frontend, and package the Spring Boot app into a JAR file.

```bash
cd backend
mvn clean package -DskipTests
```

Note: This will generate `backend/target/backend-0.0.1-SNAPSHOT.jar`, which the Electron development script looks for.

### 2. Start Electron

Once the JAR is built, you can launch the desktop shell. The `main.js` script will detect it's not packaged, launch the JAR, wait for the `data/port.txt` handshake, and open the window.

```bash
cd electron
npm install
npm start
```

---

## 🔥 Hot-Reload Development (Frontend only)

If you are strictly working on UI/CSS changes in the Vue app and want Hot Module Replacement (HMR):

1. Start the Spring Boot backend via your IDE (IntelliJ/Eclipse) to serve the API.
2. Open a terminal in `/frontend` and run:

```bash
npm install
npm run dev
```

3. Open your standard web browser to the Vite dev server URL (e.g., http://localhost:5173).

> **Note:** You may need to bypass the security handshake or copy the token from `data/port.txt` manually when running outside of Electron.

---

## 📦 Packaging for Production Release

Creating a distributable `.exe` is a two-step process that stitches together `jpackage` and `electron-builder`.

### Step 1: Create the Java App Image

Run the Maven package command. This builds the JAR and triggers the `exec-maven-plugin` to run `jpackage`, outputting a self-contained Java runtime to:

`backend/target/dist/AIToolbox`

```bash
cd backend
mvn clean package
```

### Step 2: Build the Electron Executable

Navigate to the Electron directory and run the builder. It will pull in the `jpackage` runtime as an extra resource and create the final portable executable.

```bash
cd electron
npm run dist
```

Your compiled `.exe` will be available in the `electron/dist/` folder.

---

## 🛠️ Contribution Guidelines

### 1. Branching Strategy

- **main**: The stable release branch.
- **dev** (or feature branches): Where active development happens. Create your feature branch from here (e.g., `feature/dark-mode-tweaks`).

### 2. Code Style

- **Java**: Follow standard Java conventions. Ensure you utilize Virtual Threads for heavy I/O operations to prevent locking the UI.
- **Vue/JS**: Use the Composition API (`<script setup>`) for Vue components.
- **Security**: Never expose the local API without the `HandshakeToken`. Any new API endpoints added to `SystemController` or `ImageController` must pass through the `SecurityConfig` interceptor.

### 3. Pull Requests

- Fork the repo and create your branch from `main`.
- If you've added code that should be tested, add tests.
- Ensure the build pipeline (`mvn clean package` and `npm run dist`) passes locally.
- Issue that pull request! Describe your changes, the problem it solves, and include screenshots if it is a UI change.

### 4. Known Development Quirks

- **Electron Race Condition**: If the app crashes and leaves the Java backend running as a zombie process, the `data/port.txt` file may become locked. If Electron fails to start with _"Backend failed to start"_, check your Task Manager for rogue `java.exe` processes and kill them.
- **Native Memory (ONNX)**: If working on the `ImageTaggerService`, be mindful of `OrtSession` memory leaks. Always ensure sessions are closed.

---

Thank you for helping make AI Toolbox better!
