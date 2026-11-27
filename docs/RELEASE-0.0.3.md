## Lingua Franca 公开测试版 0.0.3

默认模型改为 deepseek-flash，并自动关闭思考模式。

### 为什么
deepseek-flash 是思考模型，直接使用会让短文本翻译消耗大量推理 token（甚至返回空）。现在模组会在请求中附加 thinking disabled，让 flash 走非思考路径：

- 实测响应约 0.6 秒
- 单个物品名仅消耗 22 prompt + 3 completion token
- 既使用 flash 模型，又不会变慢或空返回

### 说明
- 自定义 baseUrl（非 DeepSeek 官方）不会附加该参数
- 仍可手动把 model 改成其它模型

### 安装
1. Fabric Loader（Minecraft 26.3，Java 25+）+ Fabric API
2. 把 lingua-franca-0.0.3.jar 放入 .minecraft/mods/
3. 启动游戏，按 J 填入 API Key

---

## Lingua Franca - Public Beta 0.0.3

Default model changed to deepseek-flash with thinking disabled.

deepseek-flash is a reasoning model by default; the mod now sends thinking disabled so short translations stay fast:

- ~0.6s response, 22 prompt + 3 completion tokens for one item name
- Custom (non-DeepSeek) endpoints do not get this parameter
- You can still set any model manually

### Install
Fabric Loader (Minecraft 26.3, Java 25+) + Fabric API, put the jar into .minecraft/mods/, press J, enter your API key.
