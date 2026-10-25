# Reliable Data Transfer — Test Report

Branch: `sonakshi-channel-emulator`

## 1. Stop-and-Wait

### Basic transfer and integrity
- Sender and receiver logs show START, DATA, and END exchanges with acknowledgements.
- The sender and receiver report the same SHA-256:
  `cb7d59307e140ac3ebdae1af3e8dafdd42511d3413275150b21c03d1569e3576`
- The recorded integrity check reports: `INTEGRITY OK (hashes match)`.
- The recorded basic transfer shows zero retransmissions.

### Impairment tests (from `emulator-test-results.txt`)
- **Packet loss:** The existing summary reports a successful transfer, one retransmission, and passing SHA-256 verification with loss=0.3 and seed=42.
- **Packet corruption:** The existing summary reports a successful transfer and passing SHA-256 verification, but explicitly notes that corruption detection or recovery was not demonstrated.
- **Delay:** The existing summary reports a successful transfer with delay=300ms and seed=42. Timeout recovery under delay was not demonstrated.

These impairment-test statements reflect the existing summary; the individual underlying impairment logs are not included in `test-results/`.

## 2. Go-Back-N

The existing Go-Back-N evidence is stored in `go-back-n-logs/`. Its committed logs are retained as-is.

## 3. Selective Repeat

`emulator-selective-repeat-loss.log` records emulator startup with loss=0.30 and seed=42. It does not establish that a Selective Repeat transfer completed or passed integrity verification. No successful-transfer claim is made.

## 4. Scope and limitations

This report summarizes existing evidence only. No tests were rerun while preparing it. Results are limited to the recorded logs and summary; unrecorded behavior is not assumed.
