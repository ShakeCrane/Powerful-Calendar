# PowerCal 文档总索引

> 更新：2026-10-21。**设计规范、已实现代码、历史交付报告是三类不同证据**。历史报告的 BUILD SUCCESSFUL、测试数和版本状态不自动代表最新 HEAD。代码行为以实际验证为准；发现规范冲突时须显式记录、评审，而非静默选一份。

## 当前决策与关键契约

| 用途 | 文档 | 使用说明 |
| --- | --- | --- |
| 最新产品冻结决策 | [Core Contract v0.9.4](CORE_CONTRACT_FREEZE_2026-10-21.md) | 2026-10-21 决策，优先于旧产品草案；仍需逐项验收 |
| 产品来源及历史决策 | [G1–G11 基线](PRODUCT_BASELINE_G1-G11.md) | 历史上下文；旧“三种事件”被新四类模型覆盖 |
| 数据稳定性与恢复 | [稳定数据契约](STABLE_DATA_CONTRACT_V1.md) | 拟冻结，勿视为已正式批准 |
| AI Agent 与授权 | [Agent 协议 v2](AI_AGENT_PROTOCOL_V2.md) | 行为、安全和接口规范 |
| Skills 演进 | [Skills 架构 v1](AI_SKILL_ARCHITECTURE_V1.md) | 目标设计不等于已实施 |
| UI/皮肤扩展 | [UI 扩展契约](UI_SKIN_EXTENSION_CONTRACT_V2.md) · [皮肤包 ABI](SKIN_PACK_V1.md) | 分清编译期接口和数据皮肤包 |
| 版本与签名 | [版本管理](RELEASE_VERSIONING.md) | Release 密钥材料须留在仓库外 |

## 当前工作与验收入口

- [2026-10-20 真机缺陷与验收](RC_REAL_DEVICE_FINDINGS_2026-10-20.md)
- [真机 UI 验收清单](GLASS_DEVICE_QA.md) · [UI 软件渲染证据](ui-evidence/README.md)
- [0.9 数据基础审计与交付（合并）](RELEASE_0_9_DATA_FOUNDATION.md) — 历史 RC 基线与未验证项
- [DSH 07–09 接口审计与交付（合并）](DSH_P0_CASE_STUDY.md) — 历史红测试、修复记录和验证结果
- [Glass UI 交付记录](GLASS_UI_DELIVERY.md) · [设计方案](POWERCAL_GLASS_DESIGN.md)
- [皮肤素材交接接口](CLAUDE_SKIN_ASSET_API.md)

## 历史资料（非当前规范）

- [初期 P0 现状审计](archive/AUDIT_P0.md)
- [v0.2 实施记录](archive/IMPLEMENTATION_V0_2.md)
- [旧版 Temporal Protocol 研究](archive/PROTOCOL_V0_RESEARCH.md)
- [已被冻结契约取代的下一阶段提案](archive/NEXT_CORE_CONTRACTS_2026_10.md)
- [旧 UI 接管备忘](archive/GEMINI_UI_HANDOFF.md)

## 维护约定

1. 规范写决策及其有效范围；实施报告写 commit、测试证据与明确的未验证项；不要把“计划”“设计”“验证通过”混为一谈。
2. 单一主题尽量维护一个当前入口；阶段性审计及交付合并时保留原始正文与时间基线，避免丢失失败证据。
3. 新决策优先更新当前冻结契约并标注所覆盖的旧规则；不直接擦除历史研究。
4. 修改文档链接后验证目标存在；提交时只暂存相关文档，避免卷入其他开发者的代码改动。
