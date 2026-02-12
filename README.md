# AI Toolbox

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Vue.js](https://img.shields.io/badge/Vue.js-3-4FC08D?style=for-the-badge&logo=vue.js&logoColor=white)
![PrimeVue](https://img.shields.io/badge/PrimeVue-3-06C167?style=for-the-badge&logo=primevue&logoColor=white)
![SQLite](https://img.shields.io/badge/Database-SQLite-003B57?style=for-the-badge&logo=sqlite&logoColor=white)
![Electron](https://img.shields.io/badge/Electron-30-47848F?style=for-the-badge&logo=electron&logoColor=white)

A robust, high-performance desktop asset manager designed specifically for the AI image generation ecosystem. It unifies metadata parsing across fragmented formats, providing **SQL-backed search**, **Smart Collections**, **live folder monitoring**, and **deep node inspection** in a modern, dark-themed desktop interface.

---

## 📸 Interface

### Core Workflow
| Library Browser | Speed Sorter ⚡ |
|:---:|:---:|
| <img src="frontend/src/assets/screenshots/browser_view.png" width="400" alt="Browser View"> | <img src="frontend/src/assets/screenshots/speedsorter_view.png" width="400" alt="Speed Sorter"> |
| *Grid Gallery with Live Monitoring* | *Rapid Organization with Keyboard Shortcuts* |

### Metadata & Inspection
| Metadata Sidebar | Deep Inspection |
|:---:|:---:|
| <img src="frontend/src/assets/screenshots/sidebar_view.png" width="400" alt="Metadata Sidebar"> | <img src="frontend/src/assets/screenshots/fullscreen_view.png" width="400" alt="Fullscreen Inspection"> |
| *Unified Parameter Display & Ratings* | *High-Res Zoom & Pan* |

<details>
<summary><b>View Advanced Features</b></summary>
<br>

| Raw Data View | Search & Filtering |
|:---:|:---:|
| <img src="frontend/src/assets/screenshots/raw_json.png" width="400" alt="Raw Metadata"> | <img src="frontend/src/assets/screenshots/search_view.png" width="400" alt="Search Interface"> |
| *Underlying JSON/Parameter Blocks* | *Instant Search* |

</details>

---

## 🔐 Portable, Private & Secure

Designed for the privacy-conscious artist, this application operates on a strictly "Local-First" philosophy.

* **Desktop Application:** Runs as a standalone desktop app via Electron, managing its own backend lifecycle.
* **100% Offline / No Telemetry:** There are no "cloud sync" features, analytics, or background API calls. Your prompts and generation data never leave your machine.
* **Privacy Scrubbing:** Integrated **Scrubber View** allows you to sanitize images before sharing. It strips hidden generation metadata (Prompts, ComfyUI Workflows, Seed data) while preserving visual quality.

---

## ✨ Key Features

* **Universal Metadata Engine:** Advanced parsing strategies for the entire stable diffusion ecosystem.
  * **ComfyUI:** Traverses complex node graphs (recursive inputs) and API formats to identify the true Sampler, Scheduler, and LoRAs used.
  * **Automatic1111 / Forge:** Robust parsing of standard "Steps: XX, Sampler: XX" text blocks.
  * **Others:** Native support for **InvokeAI**, **SwarmUI**, and **NovelAI**.
* **Library Management:**
  * **Smart Collections:** Create dynamic collections based on metadata filters (e.g., "All images using Flux model with > 4 stars").
  * **Pinned Folders:** Bookmark frequently accessed directories for rapid navigation.
  * **Star Ratings:** Rate images (1-5 stars) with instant filtering.
* **Speed Sorting:** A dedicated mode for processing high-volume generation batches.
  * **Hotkeys:** Instantly move images to configurable target folders using numeric keys (1-5).
  * **Recycle Bin:** Safely move unwanted results to the OS trash (Recycle Bin/Trash).
* **Performance:**
  * **FTS5 Search:** Powered by SQLite's Full-Text Search for near-instant results across tens of thousands of images.
  * **Virtualization:** Uses virtual scrolling to handle massive folders without UI lag.
  * **Project Loom:** Leverages Java 21 Virtual Threads for non-blocking background indexing.
* **Modern UX:**
  * **Dark Theme:** "Deep Neon Cinematic" glassmorphism styling.
  * **Image Comparator:** Side-by-side comparison tool with a draggable slider.

---

## 🛠️ Technical Architecture

The application is built as a hybrid desktop application combining a Spring Boot backend with a Vue.js frontend, packaged via Electron.

* **Backend (Java 21 + Spring Boot 3.3):**
  * **SQLite + FTS5:** High-performance local indexing and relational storage.
  * **Virtual Threads:** Optimized for heavy I/O tasks (file scanning and metadata extraction).
  * **Flyway:** Automated database schema migrations.
  * **Metadata Extractor:** Deep inspection of PNG/JPEG/WebP chunks.

* **Frontend (Vue 3 + PrimeVue):**
  * **Pinia:** Centralized state management for the image library and UI state.
  * **PrimeVue:** Premium UI component library with custom dark-green theme.
  * **Vite:** Modern build pipeline for the frontend assets.

* **Desktop (Electron):**
  * **Process Management:** Automatically spawns and terminates the Spring Boot backend.
  * **Native Integration:** Provides access to native folder selection dialogs and OS file explorer.

---

## 🚀 Getting Started

[![Download Portable Zip](https://img.shields.io/badge/Download-Portable_Zip-2ea44f?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/erroralex/ai-toolbox/releases/latest)

---

## 📜 License

Distributed under the **MIT License**. Free for personal and commercial use.

---

## 💖 Support the Project

If **AI Toolbox** has streamlined your workflow, consider supporting its ongoing development.

[![GitHub Sponsors](https://img.shields.io/badge/Sponsor-GitHub-ea4aaa?style=for-the-badge&logo=github-sponsors)](https://github.com/sponsors/erroralex)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-F16061?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/error_alex)

---

<p align="center">
  <b>Developed by</b><br>
  <img src="frontend/src/assets/alx_logo.png" width="120" alt="Alexander Nilsson Logo"><br>
  Copyright (c) 2026 Alexander Nilsson
</p>
