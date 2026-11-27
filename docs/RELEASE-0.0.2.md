## Lingua Franca 公开测试版 0.0.2

重要修复：默认模型从 deepseek-flash 改回 deepseek-chat。

### 原因
deepseek-flash 是思考模型，短文本翻译也会消耗大量推理 token（实测一个物品名约 800 token），并且容易把 token 全部用在推理上而返回空内容，表现为部分文本不翻译。

### 修复内容
- 默认模型改回 deepseek-chat（快、省、稳定）
- 旧配置中的 deepseek-flash 会在加载时自动迁移为 deepseek-chat
- 文档补充说明：翻译场景推荐 deepseek-chat

### 安装
1. Fabric Loader（Minecraft 26.3，Java 25+）+ Fabric API
2. 把 lingua-franca-0.0.2.jar 放入 .minecraft/mods/
3. 启动游戏，按 J 填入 API Key

> 需要自备 API Key，默认 DeepSeek。

---

## Lingua Franca - Public Beta 0.0.2

Important fix: default model switched back from deepseek-flash to deepseek-chat.

deepseek-flash is a reasoning model: even a short item name can consume hundreds of reasoning tokens and may return empty content, which looked like some text not being translated.

### Changes
- Default model: deepseek-chat (faster, cheaper, stable)
- Existing configs using deepseek-flash are migrated to deepseek-chat automatically
- Docs note that deepseek-chat is recommended for translation

### Install
Fabric Loader (Minecraft 26.3, Java 25+) + Fabric API, put the jar into .minecraft/mods/, press J, enter your API key.
