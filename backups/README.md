# Backups Directory

Thư mục lưu trữ tập trung các bản sao lưu database và các gói nén triển khai (deployment packages) của dự án.

## Cấu trúc:
- `database/`: Chứa các bản dump cơ sở dữ liệu (`.sql`).
- `deploy/`: Chứa các gói lưu trữ nén triển khai (`.tar.gz`).

Thư mục này được loại trừ khỏi Git repository thông qua `.gitignore` để tránh đẩy các file dữ liệu nặng lên repo.
