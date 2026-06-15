"""
3D 配载求解 HTTP 服务 (FastAPI)
启动: uv run uvicorn main:app --host=0.0.0.0 --port=8100

  POST /pack
    body: PackRequest
    return: PackResult

  POST /pack/ascii
    body: PackRequest
    return: text/plain 文字透视图（柜底视图，各层从下往上）
"""
import os
from fastapi import FastAPI, HTTPException
from fastapi.responses import PlainTextResponse, JSONResponse

import schemas
import solver
import multi_solver

app = FastAPI(
    title="xqt-saas 3D 配载求解",
    version="0.1.0",
    description="整柜装箱 + 拼柜 + LIFO 多客户卸货顺序",
)

INGEST_TOKEN = os.environ.get("XQT_STOWAGE_TOKEN", "")


def _check_token(token: str | None) -> None:
    if INGEST_TOKEN and token != INGEST_TOKEN:
        raise HTTPException(status_code=401, detail="invalid X-Ingest-Token")


@app.get("/health")
def health():
    return {"ok": True, "service": "stowage", "version": "0.1.0"}


@app.post("/pack", response_model=schemas.PackResult)
def pack(req: schemas.PackRequest, x_ingest_token: str | None = None):
    _check_token(x_ingest_token)
    try:
        return solver.solve(req)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"求解失败: {type(e).__name__}: {e}")


@app.post("/pack/multi", response_model=multi_solver.MultiPackResult)
def pack_multi(req: multi_solver.MultiPackRequest, x_ingest_token: str | None = None):
    """多柜分配: OR-Tools CP-SAT 决定每件去哪个柜 + 每柜 3D 求解。"""
    _check_token(x_ingest_token)
    try:
        return multi_solver.solve_multi(req)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"多柜求解失败: {type(e).__name__}: {e}")


@app.post("/pack/ascii", response_class=PlainTextResponse)
def pack_ascii(req: schemas.PackRequest, x_ingest_token: str | None = None):
    """求解后用 ASCII 画俯视图，方便人肉看摆放。"""
    _check_token(x_ingest_token)
    try:
        result = solver.solve(req)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"求解失败: {type(e).__name__}: {e}")
    return _ascii_view(result, req.container)


def _ascii_view(result: schemas.PackResult, container) -> str:
    """画柜底俯视图 - 按 30cm 一格栅格化"""
    grid_size = 30
    cols = int(container.length_cm // grid_size) + 1
    rows = int(container.width_cm // grid_size) + 1
    grid = [["." for _ in range(cols)] for _ in range(rows)]

    # 同客户用同字母
    cust_letters: dict[str, str] = {}
    next_letter = ord('A')
    for p in result.placements:
        cid = p.customer_id or "_"
        if cid not in cust_letters:
            cust_letters[cid] = chr(next_letter)
            next_letter = min(next_letter + 1, ord('Z'))
        ch = cust_letters[cid]
        # 占据的格子
        x0 = int(p.x_cm // grid_size)
        y0 = int(p.y_cm // grid_size)
        x1 = int((p.x_cm + p.length_cm) // grid_size)
        y1 = int((p.y_cm + p.width_cm) // grid_size)
        for r in range(max(0, y0), min(rows, y1 + 1)):
            for c in range(max(0, x0), min(cols, x1 + 1)):
                grid[r][c] = ch

    lines = []
    lines.append(f"=== 柜 {result.container_code} 俯视图 ({container.length_cm:.0f} × {container.width_cm:.0f} cm) ===")
    lines.append(f"装入: {result.fitted_count} 件 / 利用率: {result.volume_utilization:.1%} / 重量: {result.weight_used_kg}/{result.weight_max_kg} kg")
    lines.append(f"重心 (x,y,z): {result.gravity_center} cm")
    lines.append(f"4 象限重量分布 (Q1左前/Q2右前/Q3左后/Q4右后): "
                 + " / ".join(f"{q:.0%}" for q in result.gravity_quadrants))
    lines.append("")
    lines.append("门 (卸货方向 ▼)")
    lines.append("┌" + "─" * cols + "┐")
    for row in reversed(grid):
        lines.append("│" + "".join(row) + "│")
    lines.append("└" + "─" * cols + "┘")
    lines.append("内壁")
    lines.append("")
    if cust_letters:
        lines.append("图例:")
        for cid, ch in cust_letters.items():
            count = sum(1 for p in result.placements if (p.customer_id or "_") == cid)
            lines.append(f"  {ch} = 客户 {cid}  ({count} 件)")
    lines.append("")
    if result.unfitted:
        lines.append(f"⚠ 未装入 {len(result.unfitted)} 件: {', '.join(result.unfitted[:10])}"
                     + ("..." if len(result.unfitted) > 10 else ""))
    for w in result.warnings:
        lines.append(f"⚠ {w}")
    return "\n".join(lines)
