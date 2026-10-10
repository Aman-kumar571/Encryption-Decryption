# FileCrypt Deployment Guide

## Prerequisites
- A GitHub account
- A Vercel account
- Java 17 or higher installed locally (to compile the desktop JAR)
- Optional: A custom domain

---

## 1. Build the Desktop JAR

On Linux / macOS:
```bash
cd desktop
chmod +x build.sh
./build.sh
```

On Windows:
```cmd
cd desktop
build.bat
```

This compiles all sources in `desktop/src` and generates `desktop/dist/FileCrypt.jar`.

---

## 2. Copy the JAR to the Web Static Directory

Copy `desktop/dist/FileCrypt.jar` to `web/downloads/FileCrypt.jar`.

Check the SHA-256 checksum of the JAR file:
```cmd
certutil -hashfile web/downloads/FileCrypt.jar SHA256
```
Confirm that `web/index.html` matches this SHA-256 hash.

---

## 3. Push to GitHub

Initialize the Git repository and push your project:
```bash
git init
git add .
git commit -m "Initial commit of FileCrypt"
git branch -M main
git remote add origin TODO_REPO_URL
git push -u origin main
```

---

## 4. Deploy to Vercel

1. Log into your Vercel Dashboard at https://vercel.com.
2. Select **Add New...** > **Project**.
3. Import your GitHub repository.
4. In the Project Configuration:
   - **Root Directory**: `web`
   - **Framework Preset**: `Other`
   - **Build Command**: leave empty
   - **Output Directory**: leave empty
5. Click **Deploy**.

---

## 5. Custom Domain Configuration (Optional)

1. Open your project on Vercel and navigate to **Settings** > **Domains**.
2. Add your domain name (e.g., `filecrypt.example.com`).
3. Set up the corresponding DNS records in your domain provider as shown by Vercel:
   - For apex domains: `A` record pointing to `76.76.21.21`
   - For subdomains: `CNAME` record pointing to `cname.vercel-dns.com`
4. Once verified, update the domain in `web/robots.txt` and `web/sitemap.xml`.

---

## 6. Placeholder Replacements

Before sharing publicly, replace the following placeholder tokens across the repository:
- `TODO_REPO_URL`: The URL to your GitHub source repository (in `web/index.html`).
- `TODO_DOMAIN`: The live production domain (in `web/robots.txt` and `web/sitemap.xml`).
- `TODO_CONTACT_EMAIL`: The support/contact email (in `web/privacy.html` and `web/terms.html`).

---

## Pre-Launch Checklist

- [ ] JAR compiles and builds cleanly without warnings or errors.
- [ ] `com.filecrypt.SelfTest` passes 8/8 tests.
- [ ] SHA-256 checksum in `web/index.html` matches `web/downloads/FileCrypt.jar`.
- [ ] Vercel root directory is set to `web`.
- [ ] All pages (`index.html`, `tool.html`, `how-it-works.html`, `privacy.html`, `terms.html`, `404.html`) render properly.
- [ ] Browser crypto functions correctly locally via `crypto.js`.
- [ ] Cross-platform round-trip testing passes (Java can decrypt browser outputs, browser can decrypt Java outputs).
- [ ] All placeholders (`TODO_REPO_URL`, `TODO_DOMAIN`, `TODO_CONTACT_EMAIL`) are replaced with real values.
- [ ] Security headers in `web/vercel.json` are applied.