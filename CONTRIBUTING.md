# Contributing to Latent Library

We welcome contributions! Please follow these guidelines.

## Development Setup

1.  **Prerequisites:**
    *   Java 21+ (JDK)
    *   Node.js 20+ & npm
    *   Maven

2.  **Backend:**
    *   Navigate to `backend/`.
    *   Run `mvn clean install`.
    *   Run `mvn spring-boot:run`.

3.  **Frontend:**
    *   Navigate to `frontend/`.
    *   Run `npm install`.
    *   Run `npm run dev`.

4.  **Electron:**
    *   Ensure backend is built (`mvn package`).
    *   Navigate to `electron/`.
    *   Run `npm install`.
    *   Run `npm start`.
    *   Note: This will generate `backend/target/backend-1.0.1.jar`, which the Electron development script looks for.

## Code Style

*   **Java:** Follow standard Java conventions. Use 4 spaces for indentation.
*   **Vue/JS:** Use 2 spaces for indentation. Follow Vue 3 Composition API best practices.

## Pull Requests

*   Create a feature branch.
*   Submit a PR with a clear description of changes.
