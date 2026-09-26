#!/usr/bin/env python3
"""Convert an extracted Clinic Collections SQLite database into a V5 JSON backup."""

import argparse
import datetime as dt
import json
import sqlite3
import time
from pathlib import Path


def columns(connection, table):
    return {row[1] for row in connection.execute(f"PRAGMA table_info({table})")}


def row_value(row, name, default=None):
    return row[name] if name in row.keys() else default


def epoch_date(milliseconds):
    return dt.datetime.fromtimestamp(milliseconds / 1000, tz=dt.timezone.utc).date().isoformat()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("database", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    connection = sqlite3.connect(args.database)
    connection.row_factory = sqlite3.Row
    invoice_columns = columns(connection, "invoices")
    if not invoice_columns:
        raise SystemExit("The selected file has no invoices table.")

    clinics = []
    clinic_id_by_name = {}
    if columns(connection, "clinics"):
        for row in connection.execute("SELECT * FROM clinics ORDER BY id"):
            clinic = {
                "id": row["id"],
                "name": row["name"],
                "whatsappNumber": row["whatsappNumber"],
                "createdAtEpochMillis": row["createdAtEpochMillis"],
            }
            clinics.append(clinic)
            clinic_id_by_name[clinic["name"].casefold()] = clinic["id"]

    invoice_rows = list(connection.execute("SELECT * FROM invoices ORDER BY id"))
    next_clinic_id = max((item["id"] for item in clinics), default=0) + 1
    for row in invoice_rows:
        name = row["clinicName"]
        if name.casefold() not in clinic_id_by_name:
            clinic_id_by_name[name.casefold()] = next_clinic_id
            clinics.append({
                "id": next_clinic_id,
                "name": name,
                "whatsappNumber": row["whatsappNumber"],
                "createdAtEpochMillis": row["createdAtEpochMillis"],
            })
            next_clinic_id += 1

    invoices = []
    for row in invoice_rows:
        created = row["createdAtEpochMillis"]
        invoice_date = row_value(row, "invoiceDate") or epoch_date(created)
        clinic_id = row_value(row, "clinicId") or clinic_id_by_name[row["clinicName"].casefold()]
        invoices.append({
            "id": row["id"],
            "clinicId": clinic_id,
            "clinicName": row["clinicName"],
            "whatsappNumber": row["whatsappNumber"],
            "invoiceNumber": row["invoiceNumber"],
            "dueAmountMinor": row["dueAmountMinor"],
            "invoiceDate": invoice_date,
            "dueDate": row["dueDate"],
            "collectionDate": row_value(row, "collectionDate", row["dueDate"]),
            "actualPaymentDate": row_value(row, "actualPaymentDate"),
            "collectedAmountMinor": row_value(row, "collectedAmountMinor", 0),
            "paymentTerm": row_value(row, "paymentTerm", "CUSTOM"),
            "customTermDays": row_value(row, "customTermDays"),
            "manualStatus": row_value(row, "manualStatus"),
            "createdAtEpochMillis": created,
        })

    document = {
        "schemaVersion": 1,
        "exportedAtEpochMillis": int(time.time() * 1000),
        "clinics": clinics,
        "invoices": invoices,
    }
    args.output.write_text(json.dumps(document, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Converted {len(clinics)} clinics and {len(invoices)} invoices to {args.output}")


if __name__ == "__main__":
    main()
