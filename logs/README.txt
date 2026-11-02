CS30003 - BASIC FILE TRANSFER LOGS
=================================

These files are readable transcriptions of the terminal output visible in the
screenshots supplied in the chat. They are NOT original PowerShell-generated
raw log captures. A few lines (especially long SHA-256 values) have been
transcribed from the screenshots and should be checked against a fresh run
before being treated as machine-generated evidence.

Protocols demonstrated:
- Stop-and-Wait (SAW)
- Go-Back-N (GBN)
- Selective Repeat (SR)

All three transfers used test-input.txt and showed:
- 3 packets sent
- 0 retransmissions
- 0 corrupted packets dropped
- SHA-256 integrity verification: INTEGRITY OK (hashes match)

Emulator impairment settings shown:
loss=0.00, duplication=0.00, corruption=0.00, reordering=0.00,
delay=0ms, jitter=0ms, reorderDelay=100ms, seed=42.

Files:
- saw_emulator.log, saw_receiver.log, saw_sender_verify.log
- gbn_emulator.log, gbn_receiver.log, gbn_sender_verify.log
- sr_emulator.log, sr_receiver.log, sr_sender_verify.log
- transfer_summary.txt

To create authentic raw logs next time, run the commands in PowerShell with
Tee-Object -FilePath <filename> -Append. Keep the screenshots as supporting
evidence if required by your instructor.
