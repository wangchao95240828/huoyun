# 大模型 API 文本定价对照（示例）

> **说明**：单价随模型、上下文长度、是否 Batch/缓存/区域而变。下表数字为便于对比整理的 **示例档位**，**以各厂商当天官网为准**。  
> 整理时间参考：2026 年 5 月。

## 单位说明（读表前必看）

| 用语 | 含义 |
|------|------|
| **USD** | 美元（计价货币）。 |
| **1M tokens** | **一百万（1 000 000）个 tokens**；表中「输入 / 输出」列如无另注，即 **USD / 百万 tokens**。 |
| **输入 / 输出** | **输入 tokens**：请求里送进模型的部分；**输出 tokens**：模型生成部分（Gemini 等含 thinking 时官网可能对「输出」含思考 token，以官网为准）。 |
| **Kimi 行** | 国内开放平台多为 **人民币（CNY / ¥）**，与美元 **不可直接按表内数字对比**；单价以 [Moonshot 定价](https://platform.moonshot.cn/) 为准，常见写法为 **¥ / 百万 tokens**。 |
| **Gemini Flash-Lite 输入 0.25** | 对应官网 **文字 / 图片 / 视频** 输入档；**音频** 输入单价通常 **另计**（更高）。 |

## 定价表

**列单位：输入、输出 = USD / 1M tokens（美元 / 百万 tokens）**

| 厂商 | 型号 / 档位 | 输入（USD / 1M tokens） | 输出（USD / 1M tokens） | 备注 |
|------|-------------|-------------------------|-------------------------|------|
| OpenAI | GPT-5.5 | 5.00 | 30.00 | 另有 cached input；Batch 约 -50% |
| OpenAI | GPT-5.4 | 2.50 | 15.00 | 同上 |
| OpenAI | GPT-5.4 mini | 0.75 | 4.50 | 同上 |
| Anthropic | Claude Opus 4.7 / 4.6 / 4.5 | 5.00 | 25.00 | 缓存分项；Batch 折价 |
| Anthropic | Claude Sonnet 4.6 / 4.5 / 4 | 3.00 | 15.00 | 同上 |
| Anthropic | Claude Haiku 4.5 | 1.00 | 5.00 | 同上 |
| Anthropic | Claude Haiku 3 | 0.25 | 1.25 | 同上 |
| Google Gemini | 3.1 Flash-Lite（Standard Paid） | 0.25 | 1.50 | 音频输入价更高；另有 Batch/Flex/Priority |
| Google Gemini | 3.1 Pro Preview（≤200k 上下文） | 2.00 | 12.00 | 更长上下文档位更高 |
| MiniMax | M2.7 / M2.5 / M2.1 等（标准） | 0.30 | 1.20 | Prompt cache 读写另计价 |
| MiniMax | *-highspeed* | 0.60 | 2.40 | 同上 |
| Kimi（月之暗面） | 各型号（国内开放平台） | —（非 USD） | —（非 USD） | **人民币（¥）/ 百万 tokens**，按模型分档，见下方链接 |

## 官方定价入口

| 厂商 | URL |
|------|-----|
| OpenAI | https://openai.com/api/pricing/ |
| Anthropic Claude | https://platform.claude.com/docs/en/about-claude/pricing |
| Google Gemini | https://ai.google.dev/gemini-api/docs/pricing |
| MiniMax | https://platform.minimax.io/docs/guides/pricing-paygo |
| Kimi（Moonshot 国内） | https://platform.moonshot.cn/ |

## 对比时注意

1. **Tokenizer 不同**：同样一段文本，各家计得的 token 数不同，账单不能只看标价。
2. **附加费用**：缓存命中/写入、搜索/地图、音视频等可能单独计费。
3. **Kimi**：请以控制台或文档中的 **¥ / 百万 token** 与具体 **模型 ID** 为准。
