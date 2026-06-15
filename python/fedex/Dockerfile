# 用 Microsoft 官方 Playwright Python image 避免在 CentOS 7 上编译 greenlet
# (CentOS 7 自带 gcc 4.8 编不了 C++17，新 greenlet 源码需要 4.9+)
FROM mcr.microsoft.com/playwright/python:v1.49.0-jammy

WORKDIR /app

# 先复制依赖描述，让 docker layer cache 起作用
COPY pyproject.toml ./

# 装 uv
RUN pip install --no-cache-dir uv

# 复制代码
COPY . /app

# 装依赖（用 jammy image 自带 Python 3.10，有预编译 greenlet wheel）
RUN uv venv && uv sync --no-install-project

EXPOSE 8090

# patchright 用的 chromium 已经在 base image 里
CMD ["uv", "run", "main.py", "--port=8090"]
