# ─────────────────────────────────────────────────────────────
# OpenLight — Push to GitHub
# Run these commands in your terminal from inside the project folder
# ─────────────────────────────────────────────────────────────

# ── STEP 1: Create the repo on GitHub ────────────────────────
# Go to: https://github.com/new
# Fill in:
#   Repository name: openlight
#   Description:     Open-source family calendar & task manager for Android (CalDAV, GPL-3.0)
#   Visibility:      Public (required for F-Droid)
#   DO NOT check "Initialize this repository" — we're pushing our own files
# Click "Create repository"

# ── STEP 2: Unzip the project (if not already done) ──────────
unzip OpenLight.zip
cd OpenLight

# ── STEP 3: Initialize git ────────────────────────────────────
git init
git branch -M main

# ── STEP 4: Set your identity (skip if already configured) ───
git config user.name  "Your Name"
git config user.email "you@example.com"

# ── STEP 5: Add remote — replace YOUR_USERNAME ───────────────
git remote add origin https://github.com/YOUR_USERNAME/openlight.git

# ── STEP 6: Stage and commit everything ──────────────────────
git add .
git commit -m "Initial commit: OpenLight v1.0.0

Open-source family calendar & task manager for Android 8+.
CalDAV/VTODO sync, Material 3, kiosk mode, zero telemetry.
GPL-3.0"

# ── STEP 7: Push ─────────────────────────────────────────────
# GitHub will prompt for your username and a Personal Access Token (PAT)
# as the password. To create a PAT:
#   github.com → Settings → Developer settings →
#   Personal access tokens → Tokens (classic) → Generate new token
#   Check "repo" scope → Generate → copy the ghp_... token
git push -u origin main

# ─────────────────────────────────────────────────────────────
# DONE. Your repo will be live at:
# https://github.com/YOUR_USERNAME/openlight
# ─────────────────────────────────────────────────────────────

# ── OPTIONAL: Add repo topics for discoverability ────────────
# On GitHub, click the gear icon next to "About" and add:
#   android  kotlin  caldav  calendar  material-design  fdroid
#   open-source  privacy  family  kiosk  jetpack-compose

# ── OPTIONAL: F-Droid submission ─────────────────────────────
# Once the repo is public, submit at:
# https://gitlab.com/fdroid/fdroiddata
# You'll need to add metadata in their format — see:
# https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
