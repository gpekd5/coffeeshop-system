import argparse
import csv
import json
from datetime import datetime, timezone
from pathlib import Path


FIELDNAMES = [
    "recordedAt",
    "mode",
    "externalDelayMillis",
    "vus",
    "duration",
    "requests",
    "rps",
    "avgMs",
    "p95Ms",
    "p99Ms",
    "errorRate",
]


def main():
    parser = argparse.ArgumentParser(
        description="Record order event delivery k6 summary metrics as CSV."
    )
    parser.add_argument("--input", required=True, help="k6 --summary-export JSON path")
    parser.add_argument("--mode", required=True, choices=["sync", "outbox"])
    parser.add_argument("--delay", required=True, type=int)
    parser.add_argument("--vus", required=True, type=int)
    parser.add_argument("--duration", required=True)
    parser.add_argument(
        "--output",
        default="docs/performance/order-event-delivery-results.csv",
        help="CSV file to append to",
    )
    args = parser.parse_args()

    summary = read_json(Path(args.input))
    metrics = summary.get("metrics", {})
    order_duration = metric_values(metrics, "order_api_duration")
    order_failed = metric_values(metrics, "order_api_failed")
    http_reqs = metric_values(metrics, "http_reqs")

    row = {
        "recordedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "mode": args.mode,
        "externalDelayMillis": args.delay,
        "vus": args.vus,
        "duration": args.duration,
        "requests": number(http_reqs.get("count")),
        "rps": number(http_reqs.get("rate")),
        "avgMs": number(order_duration.get("avg")),
        "p95Ms": number(order_duration.get("p(95)")),
        "p99Ms": number(order_duration.get("p(99)")),
        "errorRate": number(rate_value(order_failed)),
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    append_csv(output, row)
    print(f"recorded {args.mode} delay={args.delay}ms to {output}")


def read_json(path):
    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def metric_values(metrics, name):
    metric = metrics.get(name)

    if not metric:
        raise SystemExit(f"k6 summary metric not found: {name}")

    return metric.get("values", metric)


def rate_value(values):
    if "rate" in values:
        return values.get("rate")

    return values.get("value")


def number(value):
    if value is None:
        return ""

    if isinstance(value, int):
        return str(value)

    return f"{float(value):.3f}"


def append_csv(path, row):
    write_header = not path.exists() or path.stat().st_size == 0

    with path.open("a", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=FIELDNAMES)

        if write_header:
            writer.writeheader()

        writer.writerow(row)


if __name__ == "__main__":
    main()
