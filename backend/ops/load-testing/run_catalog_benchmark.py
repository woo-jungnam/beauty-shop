"""Run the BEAUTYSHOP catalog benchmark and export a PNG from k6 measurements."""

import argparse
import csv
import json
import math
import os
import subprocess
from pathlib import Path
from urllib.parse import urlsplit

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt


def percentile(values, fraction):
    if not values:
        return math.nan
    ordered = sorted(values)
    rank = (len(ordered) - 1) * fraction
    lower = math.floor(rank)
    upper = math.ceil(rank)
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (rank - lower)


def read_results(raw_path, rates, seconds):
    stages = {
        f"catalog_{index}": {
            "target_rps": rate, "durations": [], "requests": 0, "failures": 0, "dropped": 0,
        }
        for index, rate in enumerate(rates, 1)
    }
    with raw_path.open(encoding="utf-8") as raw:
        for line in raw:
            record = json.loads(line)
            if record["type"] != "Point":
                continue
            point = record["data"]
            stage = stages.get(point.get("tags", {}).get("scenario"))
            if stage is None:
                continue
            metric, value = record["metric"], point["value"]
            if metric == "http_req_duration":
                stage["durations"].append(value)
            elif metric == "http_reqs":
                stage["requests"] += int(value)
            elif metric == "error_rate":
                stage["failures"] += int(value)
            elif metric == "dropped_iterations":
                stage["dropped"] += int(value)

    rows = []
    for stage in stages.values():
        requests = stage["requests"]
        rows.append({
            "target_rps": stage["target_rps"],
            "actual_rps": requests / seconds,
            "requests": requests,
            "p95_ms": percentile(stage["durations"], 0.95),
            "p99_ms": percentile(stage["durations"], 0.99),
            "error_percent": 100 * stage["failures"] / requests if requests else math.nan,
            "dropped": stage["dropped"],
        })
    return rows


def export_report(rows, output, seconds, base_url):
    with (output / "catalog-summary.csv").open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)

    figure, (traffic, latency) = plt.subplots(2, 1, sharex=True, figsize=(9, 6), layout="constrained")
    stages = range(len(rows))
    traffic.bar(stages, [row["actual_rps"] for row in rows], width=0.48, color="#005baa", label="Measured")
    traffic.plot(stages, [row["target_rps"] for row in rows], "o--", color="#526575", label="Target")
    traffic.set_ylabel("Requests / second")
    traffic.legend(loc="upper left", frameon=False)
    latency.plot(stages, [row["p95_ms"] for row in rows], "o-", color="#a52751", label="p95")
    latency.axhline(300, color="#526575", linestyle="--", label="p95 threshold (300 ms)")
    latency.set_ylabel("Latency (ms)")
    latency.set_ylim(bottom=0)
    latency.set_xticks(list(stages), [
        f'{row["target_rps"]} RPS\n{row["error_percent"]:.2f}% errors | {row["dropped"]} dropped'
        for row in rows
    ])
    latency.legend(loc="upper left", frameon=False)
    for axis in (traffic, latency):
        axis.set_axisbelow(True)
        axis.grid(axis="y", alpha=0.2)
    figure.suptitle(f"BEAUTYSHOP | Catalog load test\n{seconds} s per stage | {urlsplit(base_url).netloc}")
    figure.savefig(output / "catalog-benchmark.png", dpi=180)
    plt.close(figure)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080/api/v1")
    parser.add_argument("--rates", default="10,25,50,100", help="Comma-separated target requests per second")
    parser.add_argument("--seconds", type=int, default=60, help="Duration of each measured stage")
    parser.add_argument("--output", required=True, type=Path, help="New directory for benchmark results")
    args = parser.parse_args()
    try:
        rates = [int(rate) for rate in args.rates.split(",")]
    except ValueError:
        parser.error("--rates must be a comma-separated list of integers")
    if args.seconds <= 0 or any(rate <= 0 for rate in rates):
        parser.error("rates and stage duration must be positive")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    raw_name = "catalog-points.jsonl"
    script = Path(__file__).with_name("k6-catalog-browse.js").resolve()
    result = subprocess.run(
        ["k6", "run", "--out", "json=" + raw_name, str(script)],
        cwd=output,
        env={
            **os.environ,
            "BASE_URL": args.base_url.rstrip("/"),
            "CATALOG_RATES": ",".join(map(str, rates)),
            "CATALOG_STAGE_SECONDS": str(args.seconds),
            "K6_NO_USAGE_REPORT": "true",
        },
        check=False,
    )
    raw = output / raw_name
    if raw.is_file():
        rows = read_results(raw, rates, args.seconds)
        if any(row["requests"] for row in rows):
            export_report(rows, output, args.seconds, args.base_url)
            print(output / "catalog-benchmark.png", flush=True)
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
