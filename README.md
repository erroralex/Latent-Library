# Latent Library

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Vue.js](https://img.shields.io/badge/Vue.js-3-4FC08D?style=for-the-badge&logo=vue.js&logoColor=white)
![PrimeVue](https://img.shields.io/badge/PrimeVue-3-06C167?style=for-the-badge&logo=primevue&logoColor=white)
![SQLite](https://img.shields.io/badge/Database-SQLite-003B57?style=for-the-badge&logo=sqlite&logoColor=white)
![Electron](https://img.shields.io/badge/Electron-31-47848F?style=for-the-badge&logo=electron&logoColor=white)

A robust, high-performance desktop asset manager designed specifically for the AI image generation ecosystem. It unifies metadata parsing across fragmented formats, providing **SQL-backed search**, **Smart Collections**, **live folder monitoring**, and **AI-powered interrogation** in a modern, multi-themed desktop interface.

---

## 📸 Interface

<p align="center">
  <img src="frontend/src/assets/screenshots/hero_view.png" width="800" alt="Main Browser and Metadata Sidebar">
  <br>
  <i>Unified grid gallery with instant SQLite search and dynamic metadata parsing.</i>
</p>

### Rapid Organization & Inspection

<p align="center">
  <img src="frontend/src/assets/screenshots/speed_sorter.png" width="800" alt="Speed Sorter">
  <br>
  <i><b>Speed Sorter:</b> Rapidly categorize massive generation dumps using keyboard hotkeys.</i>
</p>

<p align="center">
  <img src="frontend/src/assets/screenshots/comparator.png" width="800" alt="Image Comparator Slider">
  <br>
  <i><b>Image Comparator:</b> Pixel-peep fine details between two generations with the draggable slider.</i>
</p>

<details>
<summary><b>View More Features (Collections, Duplicate Detective & Custom Themes)</b></summary>
<br>

<p align="center">
  <img src="frontend/src/assets/screenshots/dynamic_folders.png" width="800" alt="Smart Collections">
  <br>
  <i><b>Smart Collections:</b> Create dynamic, auto-populating folders based on complex metadata filters.</i>
</p>

<p align="center">
  <img src="frontend/src/assets/screenshots/duplicate_finder.png" width="800" alt="Duplicate Finder">
  <br>
  <i><b>Duplicate Detective:</b> Identify and manage identical or similar generations across your entire library.</i>
</p>

<p align="center">
  <img src="frontend/src/assets/screenshots/custom_themes.png" width="800" alt="Custom Themes">
  <br>
  <i>Choose between Deep Neon, Clean Light, and Dark Premium themes to suit your workspace.</i>
</p>

</details>

---

## 🔐 Portable, Private & Secure

Designed for the privacy-conscious artist, this application operates on a strictly "Local-First" philosophy.

* **Standalone Desktop App:** Runs as a single `.exe` (Windows), `.AppImage` (Linux), or `.dmg` (macOS). No installer required.
* **Bundled Runtime:** Includes a self-contained Java 21 environment. No system-wide Java installation is required.
* **Portable Data:** All data (database, thumbnails, settings) is stored in a local `data/` folder next to the executable (or in a standard user data location on macOS), making it easy to backup or move.
* **100% Offline / No Telemetry:** There are no "cloud sync" features, analytics, or background API calls. Your prompts and generation data never leave your machine.
* **Privacy Scrubbing:** Integrated **Scrubber View** allows you to sanitize images before sharing. It strips hidden generation metadata (Prompts, ComfyUI Workflows, Seed data) while preserving visual quality.

---

## ✨ Key Features

* **Universal Metadata Engine:** Advanced parsing strategies for the entire stable diffusion ecosystem.
  * **ComfyUI:** Traverses complex node graphs (recursive inputs) and API formats to identify the true Sampler, Scheduler, and LoRAs used.
  * **Automatic1111 / Forge:** Robust parsing of standard "Steps: XX, Sampler: XX" text blocks.
  * **Others:** Native support for **InvokeAI**, **SwarmUI**, and **NovelAI**.
  * *Note: Metadata extraction requires images to contain embedded EXIF or PNG text chunks (standard for most AI generators).*
* **AI Auto-Tagger:** Integrated **WD14 ONNX** model for local image interrogation. Automatically generate descriptive tags for your library without external API calls.
* **Library Management:**
  * **Smart Collections:** Create dynamic collections based on metadata filters (e.g., "All images using Flux model with > 4 stars").
  * **Visual Previews:** Collections feature a 3D-stacked image preview for immediate visual context.
  * **Pinned Folders:** Bookmark frequently accessed directories for rapid navigation.
  * **Star Ratings:** Rate images (1-5 stars) with instant filtering.
* **Speed Sorting:** A dedicated mode for processing high-volume generation batches.
  * **Hotkeys:** Instantly move images to configurable target folders using numeric keys (1-5).
  * **Recycle Bin:** Safely move unwanted results to the OS trash (Recycle Bin/Trash).
* **Performance:**
  * **FTS5 Search:** Powered by SQLite's Full-Text Search for near-instant results across tens of thousands of images.
  * **Virtualization:** Uses virtual scrolling to handle massive folders without UI lag.
  * **Project Loom:** Leverages Java 21 Virtual Threads for non-blocking background indexing.
* **Modern UX & Customization:**
  * **Multi-Theme System:** Choose between **Deep Neon Cinematic**, **Minimalist Light**, and **Dark Gold** themes.
  * **Image Comparator:** Side-by-side comparison tool with a draggable slider.

---

## 💻 System Requirements

* **OS:** Windows 10/11 (64-bit), Linux (AppImage), or macOS (11+).
* **Memory:** 
  * **Minimum:** 4GB RAM.
  * **Recommended:** 8GB+ RAM (especially when using the AI Auto-Tagger).
* **Storage:** ~300MB for the application + additional space for the WD14 AI model (~300MB) and thumbnail cache.
* **GPU:** Not required. AI interrogation runs efficiently on the CPU via ONNX Runtime.

---

## 🛠️ Technical Architecture

The application is built as a hybrid desktop application combining a Spring Boot backend with a Vue.js frontend, packaged via Electron.

* **Backend (Java 21 + Spring Boot 3.3):**
  * **SQLite + FTS5:** High-performance local indexing and relational storage.
  * **Virtual Threads:** Optimized for heavy I/O tasks (file scanning and metadata extraction).
  * **ONNX Runtime:** Local execution of AI models with automated native resource management and idle-eviction.
  * **Flyway:** Automated database schema migrations.

* **Frontend (Vue 3 + PrimeVue):**
  * **Pinia:** Centralized state management for the image library and UI state.
  * **PrimeVue:** Premium UI component library with custom glassmorphism overrides.
  * **Vite:** Modern build pipeline for the frontend assets.

* **Desktop (Electron):**
  * **Process Management:** Automatically spawns and terminates the Spring Boot backend.
  * **Native Integration:** Provides access to native folder selection dialogs and OS file explorer.
  * **Cross-Platform:** Builds for Windows, Linux, and macOS using GitHub Actions.

---

## 🚀 Getting Started

[![Download Latest Release](https://img.shields.io/badge/Download-Latest_Release-2ea44f?style=for-the-badge&logo=github&logoColor=white)](https://github.com/erroralex/Latent-Library/releases/latest)

1.  **Download** the appropriate file for your OS:
    *   **Windows:** `Latent Library Setup X.X.X.exe`
    *   **Linux:** `Latent Library-X.X.X.AppImage`
    *   **macOS:** `Latent Library-X.X.X.dmg`
2.  **Run** the application. No installation is required.
3.  **Select a Folder** containing your AI-generated images to start indexing.

---

## 📜 License

Distributed under the **MIT License**. Free for personal and commercial use.

---

## 💖 Support the Project

If **Latent Library** has streamlined your workflow, consider supporting its ongoing development.

[![GitHub Sponsors](https://img.shields.io/badge/Sponsor-GitHub-ea4aaa?style=for-the-badge&logo=github-sponsors)](https://github.com/sponsors/erroralex)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-F16061?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/error_alex)

---

<p align="center">
  <b>Developed by</b><br>
  <img src="frontend/src/assets/alx_logo_neon.png" width="120" alt="Alexander Nilsson Logo"><br>
  Copyright (c) 2026 Alexander Nilsson
</p>
