# 🚀 CS30003 Coding Assignment 1
## Reliable Data Transfer over UDP

---

### 📌 Project Overview

This project implements **reliable file transfer over UDP** using three transport protocols:

- **Stop-and-Wait (SAW)**
- **Go-Back-N (GBN)**
- **Selective Repeat (SR)**

It also includes a **Channel Emulator** to test protocol behaviour under different network conditions.

The project demonstrates reliable data delivery over an unreliable network using **acknowledgements, retransmissions, sequence numbers, sliding windows, and file-integrity verification**.

### 👥 Team Members and Contributions

| Team Member | Contribution |
|---|---|
| **Priya** | Selective Repeat protocol |
| **Priti** | Stop-and-Wait protocol |
| **Anustika** | Go-Back-N protocol |
| **Sonakshi** | Channel emulator and testing support |

### ✨ Key Features

- ✅ Reliable file transfer over UDP
- ✅ Three transport protocols: **SAW, GBN, and SR**
- ✅ Packet sequence numbers and acknowledgements
- ✅ Retransmission handling for packet loss
- ✅ Channel emulation under different network conditions
- ✅ **SHA-256 checksums** for file-integrity verification
- ✅ Configurable protocol parameters

### 📂 Project Structure

```text
src/
├── app/
├── common/
├── emulator/
├── network/
├── protocol/
└── transport/
```

### ⚙️ Requirements

- **Java Development Kit (JDK)**
- Terminal or command prompt
- **Visual Studio Code** (recommended)

### 🔨 Compilation

Open a terminal in the project root directory and run:

```powershell
javac -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
```

### ▶️ Running the Project

Run the application's main class using the required command-line arguments and protocol options.

The supported protocol implementations are:

- `saw` — Stop-and-Wait
- `gbn` — Go-Back-N
- `sr` — Selective Repeat

Refer to the assignment instructions and application command-line options for the complete execution procedure.

### 🧪 Testing and Verification

The project includes tests covering:

- **Adaptive RTO behaviour**
- **Selective Repeat protocol**
- **Packet checksum and integrity**
- **End-to-end file transfer**

File integrity is verified using **SHA-256 hash comparison** between the original and received files.

### 📦 Repository Information

This repository contains the source code, protocol implementations, channel emulator, tests, and experimental results for **CS30003 Coding Assignment 1 — Reliable Data Transfer over UDP**.

---

<p align="center">
  <b>CS30003 | Computer Networks</b><br>
  Reliable Data Transfer over UDP
</p>