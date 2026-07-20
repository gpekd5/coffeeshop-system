import argparse
import csv
from collections import defaultdict
from pathlib import Path


WIDTH = 920
HEIGHT = 520
PADDING_LEFT = 90
PADDING_RIGHT = 40
PADDING_TOP = 70
PADDING_BOTTOM = 80
COLORS = {
    "sync": "#d94841",
    "outbox": "#2f855a",
}


def main():
    parser = argparse.ArgumentParser(
        description="Create an SVG p95 comparison chart from order performance CSV."
    )
    parser.add_argument(
        "--input",
        default="docs/performance/order-event-delivery-results.csv",
        help="CSV file created by record_k6_order_event_result.py",
    )
    parser.add_argument(
        "--output",
        default="docs/performance/order-event-delivery-comparison.svg",
        help="SVG chart output path",
    )
    args = parser.parse_args()

    rows = read_rows(Path(args.input))
    svg = build_svg(rows)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(svg, encoding="utf-8")
    print(f"wrote {output}")


def read_rows(path):
    if not path.exists():
        raise SystemExit(f"CSV file not found: {path}")

    with path.open("r", encoding="utf-8", newline="") as file:
        rows = [
            row for row in csv.DictReader(file)
            if row.get("mode") and row.get("externalDelayMillis") and row.get("p95Ms")
        ]

    if not rows:
        raise SystemExit("CSV has no plottable rows.")

    return rows


def build_svg(rows):
    grouped = defaultdict(list)

    for row in rows:
        grouped[row["mode"]].append((
            float(row["externalDelayMillis"]),
            float(row["p95Ms"]),
        ))

    for points in grouped.values():
        points.sort(key=lambda point: point[0])

    all_x = [point[0] for points in grouped.values() for point in points]
    all_y = [point[1] for points in grouped.values() for point in points]
    min_x = min(all_x)
    max_x = max(all_x)
    max_y = max(all_y) * 1.15

    if min_x == max_x:
        max_x = min_x + 1

    if max_y <= 0:
        max_y = 1

    elements = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}">',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        text(WIDTH / 2, 34, "Sync vs Outbox/Kafka order API p95 latency", 22, "#1f2937", "middle", "700"),
        axis_line(PADDING_LEFT, HEIGHT - PADDING_BOTTOM, WIDTH - PADDING_RIGHT, HEIGHT - PADDING_BOTTOM),
        axis_line(PADDING_LEFT, PADDING_TOP, PADDING_LEFT, HEIGHT - PADDING_BOTTOM),
        text(WIDTH / 2, HEIGHT - 24, "External mock API delay (ms)", 14, "#374151", "middle", "600"),
        text(28, HEIGHT / 2, "p95 latency (ms)", 14, "#374151", "middle", "600", rotate=-90),
    ]

    for tick in range(0, 6):
        ratio = tick / 5
        y_value = max_y * (1 - ratio)
        y = scale_y(y_value, max_y)
        elements.append(axis_line(PADDING_LEFT, y, WIDTH - PADDING_RIGHT, y, "#e5e7eb"))
        elements.append(text(PADDING_LEFT - 12, y + 5, f"{y_value:.0f}", 12, "#6b7280", "end"))

    x_ticks = sorted(set(all_x))

    for x_value in x_ticks:
        x = scale_x(x_value, min_x, max_x)
        elements.append(axis_line(x, HEIGHT - PADDING_BOTTOM, x, HEIGHT - PADDING_BOTTOM + 6, "#6b7280"))
        elements.append(text(x, HEIGHT - PADDING_BOTTOM + 24, f"{x_value:.0f}", 12, "#6b7280", "middle"))

    legend_y = PADDING_TOP - 22

    for index, mode in enumerate(["sync", "outbox"]):
        if mode not in grouped:
            continue

        color = COLORS.get(mode, "#374151")
        legend_x = PADDING_LEFT + index * 140
        elements.append(f'<line x1="{legend_x}" y1="{legend_y}" x2="{legend_x + 28}" y2="{legend_y}" stroke="{color}" stroke-width="4"/>')
        elements.append(text(legend_x + 36, legend_y + 5, mode, 13, "#374151", "start", "600"))
        points = grouped[mode]
        scaled_points = [
            (scale_x(x, min_x, max_x), scale_y(y, max_y))
            for x, y in points
        ]
        elements.append(polyline(scaled_points, color))

        for x, y in scaled_points:
            elements.append(f'<circle cx="{x:.2f}" cy="{y:.2f}" r="5" fill="{color}"/>')

    elements.append("</svg>")

    return "\n".join(elements) + "\n"


def scale_x(value, min_x, max_x):
    chart_width = WIDTH - PADDING_LEFT - PADDING_RIGHT

    return PADDING_LEFT + ((value - min_x) / (max_x - min_x)) * chart_width


def scale_y(value, max_y):
    chart_height = HEIGHT - PADDING_TOP - PADDING_BOTTOM

    return PADDING_TOP + (1 - (value / max_y)) * chart_height


def axis_line(x1, y1, x2, y2, color="#374151"):
    return f'<line x1="{x1:.2f}" y1="{y1:.2f}" x2="{x2:.2f}" y2="{y2:.2f}" stroke="{color}" stroke-width="1"/>'


def polyline(points, color):
    joined = " ".join(f"{x:.2f},{y:.2f}" for x, y in points)

    return f'<polyline points="{joined}" fill="none" stroke="{color}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>'


def text(x, y, body, size, color, anchor, weight="400", rotate=None):
    transform = ""

    if rotate is not None:
        transform = f' transform="rotate({rotate} {x} {y})"'

    return (
        f'<text x="{x:.2f}" y="{y:.2f}" fill="{color}" font-family="Arial, sans-serif" '
        f'font-size="{size}" font-weight="{weight}" text-anchor="{anchor}"{transform}>{body}</text>'
    )


if __name__ == "__main__":
    main()
