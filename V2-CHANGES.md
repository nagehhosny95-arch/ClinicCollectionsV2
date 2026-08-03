# Clinic Collections v2

## Delivered

- Fixed `INV/2026/` prefix with suffix-only entry and validation.
- Material 3 date pickers for Due, Collection, and Actual Paid dates.
- Payment Term removed from every visible screen and from business behavior.
- Dedicated required Collection Date stored in Room.
- Safe Room migration 1 → 2; old records receive Collection Date = their existing Due Date.
- Green Paid, orange Collection Today, red Collection Overdue, and neutral Upcoming cards.
- Collection Date drives the two scheduled reminders.
- Add, edit, paid action, delete confirmation, WhatsApp reminder, dashboard, empty states, reports, and About screen.
- Monthly report selector for Due Date or Collection Date.
- Branding footer with clickable `+971508984903`.
- Existing GitHub Actions APK workflow retained.

## Upgrade check

Install the new APK over version 1 without uninstalling it. Existing local records are migrated when the app first opens. Uninstalling the old app first would remove its local-only database.
