# 用 Microsoft 官方 Playwright Python image 避免在 CentOS 7 上编译 greenlet
# (CentOS 7 自带 gcc 4.8 编不了 C++17，新 greenlet 源码需要 4.9+)
FROM mcr.microsoft.com/playwright/python:v1.60.0-jammy

WORKDIR /app

# 国内 PyPI 镜像（清华），避免默认 PyPI.org 慢
ENV PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple \
    UV_DEFAULT_INDEX=https://pypi.tuna.tsinghua.edu.cn/simple \
    UV_PYPI_TIMEOUT=120

# 先复制依赖描述，让 docker layer cache 起作用
COPY pyproject.toml ./

# 装 uv
RUN pip install --no-cache-dir uv

# 复制代码
COPY . /app

# 装依赖（用 jammy image 自带 Python 3.10，有预编译 greenlet wheel）
RUN uv venv && uv sync --no-install-project

# patchright 1.60.1 期待 chromium-1223; base image 自带 chromium-1222；让 patchright 自己装匹配
RUN uv run patchright install chromium --no-shell || uv run patchright install chromium

EXPOSE 8090

# 必须 --headless=True：容器没 X server；click bool flag 不能用 --headless 单 flag
CMD ["uv", "run", "main.py", "--port=8090", "--headless=True"]
