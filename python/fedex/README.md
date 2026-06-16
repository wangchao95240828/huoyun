### 启动

```sh
uv run main.py --port={端口}
```

### 调用

```
GET /{运单号}?timeout={超时时间}
```

- 运单号: `string` 符合正则 `^\d+$`
- 超时时间: `float` 单位秒，默认 `30`


### 输出

参照 `main.py` 中的 `GetResponse`