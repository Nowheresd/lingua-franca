## Lingua Franca 公开测试版 0.0.1

首个公开测试版：把服务器里的英文实时翻译成中文。

### 功能
- 💬 聊天消息
- 🪧 物品名称与 Lore、悬停提示
- 🏷️ 世界浮空字、木牌
- 🏆 成就名称与描述
- 📊 Tab 列表、计分板、Boss 条、动作栏
- 🗣️ Wynncraft NPC 对话（名字 / 正文 / 选项）
- 🧠 分服本地缓存：每句话只翻译一次，重复出现直接命中
- ⚡ 请求按来源分车道调度，不互相抢占

### 安装
1. 安装 **Fabric Loader**（Minecraft **26.3**，Java **25+**）与 **Fabric API**
2. 把 `lingua-franca-0.0.1.jar` 放入 `.minecraft/mods/`
3. 启动游戏，按 **J** 打开设置，填入翻译服务 **API Key**
4. 进入服务器即可生效

> 需要自备 API Key。默认使用 DeepSeek（`https://api.deepseek.com`，模型 `deepseek-flash`），也支持任意兼容 OpenAI 格式的服务。

### 说明
- 纯客户端，无需服务器安装
- 不修改资源包与服务器数据
- 已知问题：部分特殊字体 / 第三方模组自绘文本可能不翻译；首次遇到的新文本需要一次网络翻译

---

## Lingua Franca — Public Beta 0.0.1

Real-time Chinese translation for English server text in Minecraft 26.3.

### Features
- Chat, item names & lore, tooltips
- Holograms and signs
- Advancements, tab list, scoreboard, boss bars, action bar
- Wynncraft NPC dialogue
- Per-server local cache (each phrase translated once)
- Modular request lanes so one busy UI cannot block others

### Install
1. Fabric Loader (Minecraft 26.3, Java 25+) + Fabric API
2. Put the jar into `.minecraft/mods/`
3. Press **J** and enter your API key
4. Join a server

> Bring your own API key (default: DeepSeek `deepseek-flash`; any OpenAI-compatible endpoint works).

### Notes
- Client-side only, no resource pack / server changes
- Beta: some special fonts may not translate; new phrases need one network round-trip
