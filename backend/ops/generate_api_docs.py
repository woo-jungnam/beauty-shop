"""Publish API reference from the snapshot produced by passing contract tests."""
from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
BUILD = ROOT / "target-spa-validation"
SOURCE = BUILD / "api-docs" / "openapi.json"
REPORT = BUILD / "surefire-reports" / "TEST-com.core.beautyshop.OpenApiContractIntegrationTest.xml"
METHODS = {"get", "post", "put", "patch", "delete", "head", "options", "trace"}
MODULES = [
    ("identity", "Xác thực, tài khoản và vai trò"),
    ("catalog", "Sản phẩm và danh mục"),
    ("cart", "Giỏ hàng"),
    ("order", "Đơn hàng và hoàn tiền"),
    ("payment", "Thanh toán và webhook"),
    ("spa", "Dịch vụ, liệu trình và lịch hẹn Spa"),
    ("crm", "Hồ sơ và chăm sóc khách hàng"),
    ("inventory", "Kho và kiểm kê"),
    ("procurement", "Nhà cung cấp và mua hàng"),
    ("promotion", "Khuyến mãi"),
    ("review", "Đánh giá và kiểm duyệt"),
    ("dashboard", "Dashboard"),
    ("system", "Cấu hình, cảnh báo và kiểm toán"),
    ("chatbot", "Chatbot"),
]


def check_refs(node: object, root: dict) -> None:
    if isinstance(node, dict):
        ref = node.get("$ref", "")
        if ref.startswith("#/"):
            target: object = root
            try:
                for part in ref[2:].split("/"):
                    part = part.replace("~1", "/").replace("~0", "~")
                    target = target[int(part)] if isinstance(target, list) else target[part]
            except (KeyError, IndexError, TypeError, ValueError) as exc:
                raise SystemExit(f"Unresolved OpenAPI reference: {ref}") from exc
        for child in node.values():
            check_refs(child, root)
    elif isinstance(node, list):
        for child in node:
            check_refs(child, root)


def cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ").replace("\r", " ")


def publish() -> None:
    if not SOURCE.is_file() or not REPORT.is_file():
        raise SystemExit("Run OpenApiContractIntegrationTest successfully before generating docs.")
    suite = ET.parse(REPORT).getroot()
    if int(suite.attrib.get("tests", 0)) < 6 or any(int(suite.attrib.get(key, 0)) for key in ("failures", "errors", "skipped")):
        raise SystemExit("API contract verification has not passed completely.")
    if SOURCE.stat().st_mtime > REPORT.stat().st_mtime:
        raise SystemExit("Snapshot was written after the passing suite report; rerun verification.")
    inputs = list((ROOT / "src" / "main" / "java").rglob("*.java")) + [
        ROOT / "src" / "main" / "resources" / "application.yaml",
        ROOT / "src" / "test" / "java" / "com" / "core" / "beautyshop" / "OpenApiContractIntegrationTest.java",
        ROOT / "src" / "test" / "resources" / "application-test.yaml",
        ROOT / "pom.xml",
    ]
    if any(path.stat().st_mtime > REPORT.stat().st_mtime for path in inputs):
        raise SystemExit("Source changed after verification; rerun the contract tests.")
    document = json.loads(SOURCE.read_text(encoding="utf-8"))
    check_refs(document, document)
    operations = [
        (path, method, operation)
        for path, item in document["paths"].items()
        for method, operation in item.items()
        if method in METHODS
    ]
    known = {key for key, _ in MODULES}
    if any(operation.get("x-module") not in known for _, _, operation in operations):
        raise SystemExit("An API has no documented module; update the generator/module metadata.")
    lines = [
        "# Danh mục endpoint API",
        "",
        f"Sinh từ OpenAPI đã kiểm chứng lúc {datetime.now().astimezone().isoformat(timespec='seconds')}. "
        f"**{len(operations)} operations trên {len(document['paths'])} đường dẫn.**",
        "",
        "Nguồn chi tiết: [OpenAPI JSON](openapi.json), [hướng dẫn Swagger](API_GUIDE.md). "
        "Bảng ghi HTTP thành công; lỗi và schema request/response tra trong Swagger. "
        "Role ở guard chưa thay điều kiện ownership/phân công/state của service.",
        "",
    ]
    for module, title in MODULES:
        rows = [(path, method, operation) for path, method, operation in operations if operation.get("x-module") == module]
        if not rows:
            continue
        lines += [f"## {title} — {len(rows)} operations", "", "| Method | Path | Hành động | Xác thực / guard | HTTP thành công |", "| --- | --- | --- | --- | --- |"]
        for path, method, operation in sorted(rows, key=lambda row: (row[0], row[1])):
            access = operation.get("x-method-authorization") or {
                "public": "Công khai",
                "optional-bearer": "JWT tùy chọn / phiên khách",
                "bearer": "JWT; kiểm thêm phạm vi tại service",
                "sepay-key": "API key SePay",
            }[operation["x-access-mode"]]
            success = ", ".join(sorted(code for code in operation.get("responses", {}) if code.startswith("2")))
            lines.append(f"| {method.upper()} | `{path}` | {cell(operation.get('summary', ''))} | {cell(access)} | {success} |")
        lines.append("")
    destination = ROOT / "docs"
    destination.mkdir(exist_ok=True)
    (destination / "openapi.json").write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (destination / "API_ENDPOINTS.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Published {len(operations)} operations / {len(document['paths'])} paths to backend/docs")


if __name__ == "__main__":
    publish()
