FROM python:3.11-slim

WORKDIR /app

# 清华 PyPI 镜像
ENV PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple

RUN pip install --no-cache-dir fastapi uvicorn pydantic numpy ortools matplotlib

COPY . /app

EXPOSE 8100

CMD ["uvicorn", "main:app", "--host=0.0.0.0", "--port=8100"]
