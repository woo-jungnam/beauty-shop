# Project Logs Directory

Thư mục lưu trữ tập trung toàn bộ file log của hệ thống nhằm tránh phát sinh file rải rác ở thư mục gốc (root directory).

## Cấu trúc và quy ước:
- Tất cả log tiến trình build, deploy, kiểm thử hoặc run command cần redirect stdout/stderr vào thư mục này:
  - Ví dụ: `npm run build > logs/frontend-build.log 2>&1`
  - Ví dụ: `docker compose up > logs/docker-compose.log 2>&1`
- Spring Boot backend được cấu hình ghi log tự động theo rolling policy vào:
  - `logs/` (mặc định thông qua biến `LOG_PATH` trong `application.yaml`).
- Thư mục này đã được thêm vào `.gitignore` (ngoại trừ file `.gitkeep` và `README.md`) để không làm ô nhiễm repository Git.
