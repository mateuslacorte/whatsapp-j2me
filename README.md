# WhatsApp J2ME

A functional Java ME (J2ME) messaging client built for vintage and feature phones. This project brings modern messaging connectivity to classic mobile devices through a dedicated companion bridge and backend service.

## ✨ Features & Overview

- **Chat & Conversation Views:** Clean, classic mobile interface for browsing chats and reading messages.
- **Contact & Message Models:** Local data handling and storage for contacts and conversations.
- **QR-Based Session Flow:** Seamless account linking via QR code authentication.
- **Real-Time Notifications:** Instant alerts for incoming messages.
- **Broad Compatibility:** Built on the **MIDP 2.0 / CLDC 1.1** specification to support a wide range of legacy devices.

> **Backend Requirement:** This client connects to a companion backend server to handle WhatsApp Web connectivity. If you want to self-host your own instance, the official server repository is available at **[mateuslacorte/baileys-j2me](https://github.com/mateuslacorte/baileys-j2me)**.

## 🚀 Getting Started & Pre-Compiled Builds

If you prefer not to compile the J2ME source code yourself, you can choose between two ready-to-use options:

### 1. Free Public Build (Hosted Backend)
Want to start messaging right away without setting up a server? You can download the pre-compiled `.jar`/`.jad` files already configured to use my official backend server:
👉 **[Download at j2me.lacorte.dev](https://j2me.lacorte.dev)**

### 2. Custom Pre-Compiled Build (Self-Hosted Backend)
If you are hosting your own backend using **[baileys-j2me](https://github.com/mateuslacorte/baileys-j2me)** and don't know how (or don't want) to set up an old-school J2ME build environment, you can get a **custom-compiled version pre-configured with your own server URL**:
- Subscribe to my membership club at **[members.lacorte.dev](https://members.lacorte.dev)** for just **$1/month**.
- Alongside getting your custom build, you unlock several member perks and directly support the continued development of my projects.
- *No strings attached: you are free to subscribe for a single month just to generate your custom build and cancel anytime.*

## 📁 Project Structure

- `src/` — Java ME application source code
- `res/` — Application icons, images, and static resources

## ⚠️ Disclaimer

This is an independent project and is not affiliated with, endorsed by, or sponsored by WhatsApp LLC or Meta Platforms, Inc. WhatsApp and all related trademarks are the property of their respective owners.
