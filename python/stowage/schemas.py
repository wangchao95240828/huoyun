"""3D 配载请求/响应数据模型 — pydantic v2"""
from __future__ import annotations
from typing import Optional, Literal
from pydantic import BaseModel, Field, NonNegativeFloat, PositiveFloat


# ─── 请求 ───

class Container(BaseModel):
    """集装箱/卡车货厢规格"""
    code: str = Field(description="柜代号 如 '40HC-001'")
    length_cm: PositiveFloat
    width_cm: PositiveFloat
    height_cm: PositiveFloat
    max_weight_kg: PositiveFloat


class Item(BaseModel):
    """单件待装货物（一票/一箱）"""
    sku: str = Field(description="物品识别号或 carton_no")
    length_cm: PositiveFloat
    width_cm: PositiveFloat
    height_cm: PositiveFloat
    weight_kg: NonNegativeFloat
    customer_id: Optional[str] = Field(default=None,
        description="哪位客户的货，用于 LIFO 排序")
    this_side_up: bool = Field(default=False,
        description="True = 不可倒置（电池/液体）")
    fragile: bool = Field(default=False,
        description="True = 上不可压（脆弱品）")
    load_bearing: NonNegativeFloat = Field(default=0,
        description="可承重 kg；0 = 不可堆压")
    customer_priority: int = Field(default=0,
        description="同客户内细排序（数字小先卸）")


class PackRequest(BaseModel):
    container: Container
    items: list[Item] = Field(min_length=1)
    route: list[str] = Field(default_factory=list,
        description="客户卸货顺序 customer_id 数组；先到的在前；启用 LIFO 后会反向排进柜")
    enable_lifo: bool = Field(default=True,
        description="是否启用 LIFO 多客户卸货顺序（先卸的放外侧/后装）")
    number_of_decimals: int = Field(default=2)


# ─── 响应 ───

class Placement(BaseModel):
    sku: str
    customer_id: Optional[str] = None
    # 几何放置位置（坐标系：起点 0,0,0，柜内 +X = length, +Y = width, +Z = height）
    x_cm: float
    y_cm: float
    z_cm: float
    # 旋转后的有效尺寸
    length_cm: float
    width_cm: float
    height_cm: float
    rotation_type: int = Field(description="0-5: py3dbp 6 种旋转")
    weight_kg: float


class PackResult(BaseModel):
    container_code: str
    fitted_count: int
    unfitted_count: int
    volume_utilization: float = Field(description="0-1，装柜空间利用率")
    weight_used_kg: float
    weight_max_kg: float
    gravity_center: tuple[float, float, float] = Field(
        description="重心相对柜内坐标 (cx, cy, cz) cm")
    gravity_quadrants: list[float] = Field(default_factory=list,
        description="柜底分 4 象限的重量分布，理想各 25%")
    placements: list[Placement]
    unfitted: list[str] = Field(default_factory=list,
        description="装不进的 SKU 列表")
    warnings: list[str] = Field(default_factory=list)
