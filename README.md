# CS30003 Coding Assignment 1: Reliable Data Transfer over UDP

## Project Overview

This project implements reliable file transfer over UDP using three transport protocols: Stop-and-Wait, Go-Back-N, and Selective Repeat. It also includes a channel emulator for testing protocol behaviour under different network conditions.

The project demonstrates reliable data delivery over an unreliable network using acknowledgements, retransmissions, sequence numbers, sliding windows, and file-integrity verification.

## Team Members and Contributions

- **Priya** — Selective Repeat protocol
- **Preeti** — Stop-and-Wait protocol
- **Anustika** — Go-Back-N protocol
- **Sonakshi** — Channel emulator and testing support

## Features

- Reliable file transfer over UDP
- Stop-and-Wait, Go-Back-N, and Selective Repeat protocols
- Packet sequence numbers and acknowledgements
- Retransmission handling for packet loss
- Channel emulation for testing network conditions
- SHA-256 checksums for verifying file integrity
- Configurable protocol parameters

## Project Structure

src/
├── app/
├── common/
├── emulator/
├── network/
├── protocol/
└── transport/

## Requirements

- Java Development Kit (JDK)
- Terminal or command prompt

## Compilation

From the project root directory, run:

powershell
javac -d out (Get-ChildItem -Recurse src -Filter *.java).FullName

## Running the Project

Run the application's main class using the command and protocol options documented in the source code or assignment instructions.

Supported protocol implementations include Stop-and-Wait, Go-Back-N, and Selective Repeat. Check the application's command-line arguments before running a transfer.

## Testing

The project includes test classes for protocol behaviour, packet integrity, adaptive retransmission timeout, and channel-emulator-based transfer testing.

File integrity can be checked by comparing the SHA-256 hashes of the original and received files.

## Repository

This repository contains the source code for CS30003 Coding Assignment 1 — Reliable Data Transfer over UDP.

