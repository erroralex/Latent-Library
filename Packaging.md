# Packaging & Release Guide (GitHub Actions)

This guide explains how to build and release **Latent Library** for Windows, Linux, and macOS using GitHub Actions.

## Prerequisites

*   You must be on the `development` branch (or whichever branch you are working on).
*   You must have committed all your latest code changes.
*   You are using **Git Bash** on Windows.

---

## 1. Commit Your Changes

Ensure your local code is up to date and committed.

```bash
git add .
git commit -m "Your commit message here"
git push origin development
```

## 2. Trigger a Build (Create a Release)

The build pipeline is triggered **only** when you push a tag starting with `v` (e.g., `v1.0.2`).

### Option A: New Release (Normal Flow)

If everything is working fine and you want to release a new version (e.g., going from `1.0.2` to `1.0.3`):

1.  **Update Version Numbers:**
    *   Update `version` in `electron/package.json` and `frontend/package.json`.
    *   Update `<version>` in `backend/pom.xml`.
    *   Commit these changes: `git commit -am "Bump version to 1.0.3"` & `git push`.

2.  **Tag and Push:**
    ```bash
    git tag v1.0.4
    git push origin v1.0.4
    ```

### Option B: Retry a Failed Build (Fixing Errors)

If a build failed for version `v1.0.2` and you want to retry **without** changing the version number to `1.0.3`:

1.  **Delete the tag locally:**
    ```bash
    git tag -d v1.0.2
    ```

2.  **Delete the tag remotely (GitHub):**
    ```bash
    git push --delete origin v1.0.2
    ```

3.  **Push your fixes:**
    Make your code changes, commit them, and push to your branch.
    ```bash
    git add .
    git commit -m "Fix build configuration"
    git push origin development
    ```

4.  **Re-create and push the tag:**
    ```bash
    git tag v1.0.2
    git push origin v1.0.2
    ```

---

## 3. Monitor the Build

1.  Go to your GitHub Repository.
2.  Click the **Actions** tab.
3.  Click on the **Build and Release** workflow.
4.  Watch the jobs (`Build on windows-latest`, `ubuntu-latest`, `macos-latest`).

## 4. Download Artifacts

Once all jobs are green:
1.  Go to the **Releases** tab on GitHub.
2.  You will see the new release.
3.  Download the files under **Assets**:
    *   `.exe` (Windows)
    *   `.AppImage` (Linux)
    *   `.dmg` (macOS)
