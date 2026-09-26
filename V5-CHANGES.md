# V5 changes

- Moved Home search and status filters to the top.
- Added clickable Collection Due, Payment Overdue, and Collected This Month lists with the
  required chronological ordering.
- Added Invoice Date, Room migration 3 → 4, and invoice-year prefixes that follow Invoice Date.
- Rebuilt Reports around yearly totals, twelve separate months, month drill-down, and clinic
  payment history.
- Implemented the approved cash collection percentage, including values above 100% and `N/A`
  when a month has no issued invoices.
- Added user-controlled JSON Backup/Restore with strict validation and transactional replacement.
- Added Advance Medical branding with clear developer credit for Nageh Hosny and his contact number.
- Added GitHub unit/lint/debug jobs, Android emulator migration/restore tests, and permanent-key
  signed release support.
- Added a one-time ADB database conversion route for data trapped in the old debug-signed app.
