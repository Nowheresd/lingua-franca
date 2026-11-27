# 更新日志

## v0.0.1 — 公开测试版（Beta）

首个公开测试版：把服务器里的英文实时翻译成中文。

### 功能
- 聊天、物品名 / Lore、悬停提示实时翻译
- 浮空字、木牌、实体名翻译
- 成就、Tab 列表、计分板、Boss 条、动作栏翻译
- Wynncraft NPC 对话翻译（名字 / 正文 / 选项）
- 分服缓存与分服词库，翻译结果本地复用
- 设置界面（J 键打开），可分别开关各模块
- 默认翻译模型 deepseek-flash，并在请求中自动关闭思考模式（thinking disabled），保证短文本翻译速度
- translateUi 联动接口，兼容需要英文原文的第三方界面

### 性能
- 翻译请求按来源拆分为 6 条车道，互不抢占
- 双层结果缓存 + 进服缓存异步加载
- 在途请求上限 400、预取批量上限 300

### 已知问题
- 部分服务器 / 第三方模组使用特殊字体时可能不翻译
- 首次遇到的新文本需要一次网络翻译
- 极少数界面可能在翻译后出现排版偏移，欢迎反馈

---

# Changelog (EN)

## v0.0.1 — Public Beta

First public beta: real-time Chinese translation for English server text.

- Chat, item names/lore, tooltips, holograms, signs, advancements, tab list, scoreboard, boss bars
- Wynncraft NPC dialogue
- Per-server cache and glossary
- In-game settings screen (J)
- Default model deepseek-flash with thinking disabled (fast short-text translation)
- Modular request lanes, double-layer cache, async cache loading

Known issues: special fonts may not translate; new phrases need one network round-trip.
