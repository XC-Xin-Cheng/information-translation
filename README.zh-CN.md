# Information Translation

MC **26.1 Fabric** 纯客户端翻译模组:把游戏里的**聊天、成书(书本)、告示牌**内容交给免费 AI 模型翻译,目标语言可自定义。

- **纯客户端**:不需要服务端安装,进任何服务器(包括原版服)都能用。
- **整段翻译**:不做单行断句——一次请求翻译整个告示牌(4 行)、一批聊天消息、整本/整页书,上下文不丢、省请求次数。
- **免费模型**:默认走 [Pollinations](https://pollinations.ai)(浏览器上直接对话的那类免费模型,无需 API Key);也可以切换到任意 **OpenAI 兼容**接口(OpenRouter 免费模型、Groq、硅基流动、本地 Ollama 等)或 Google 翻译备用源。
- **Mod Menu 支持**:Mod Menu 里点击"设置"即可打开配置界面(或按 **O** 键)。

## 构建

需要 **JDK 25**(26.1 的硬性要求):

```bash
JAVA_HOME="C:\Program Files\Zulu\zulu-25" ./gradlew build
# Windows cmd: set JAVA_HOME=C:\Program Files\Zulu\zulu-25 && gradlew.bat build
```

产物:`build/libs/information-translation-<版本>.jar`(把不带 `-sources` 的那个丢进 `.minecraft/mods`)。

## 依赖

- Fabric Loader ≥ 0.20.1
- Fabric API(0.145.1+26.1 或更高)
- 可选:Mod Menu(打开设置菜单用)

## 使用

1. 装好模组进入游戏,按 **O**(可在"按键设置 → 杂项"里改)或在 Mod Menu 中点开 **Information Translation → 设置**。
2. 选择目标语言(可预设可直接输入,如 `简体中文`、`English`、`日本語`……)。
3. 翻译源:
   - **Pollinations(默认,免费无需密钥)**:默认模型 `openai`,API 地址 `https://text.pollinations.ai/openai`。
   - **自定义 OpenAI 兼容接口**:填 `API 地址`(如 `https://api.groq.com/openai/v1/chat/completions`)、`模型`、`API 密钥`。
   - **Google 翻译(备用)**:无需任何密钥,按常见语言名自动映射语言代码。
4. 分项开关:翻译聊天 / 翻译书本 / 翻译告示牌 / 总开关。点 **测试翻译** 验证连通性。

### 效果

- **聊天**:原消息下方追加一行灰色斜体 `↳ 译文`;多条消息会合并成一次请求批量翻译。
- **书本**:阅读界面右上角出现 **译** 按钮(讲台阅读也支持),点击后整本翻译并打开译文阅读界面,可随时 `原文/译文` 切换对照,译文按页渐进显示。
- **告示牌**:靠近视线的告示牌自动整牌(4 行)翻译,几秒内原地替换显示;同一内容有缓存,不会重复请求。

## 配置文件

`config/information_translation.json`(游戏内改完自动保存):

| 字段 | 说明 | 默认 |
| --- | --- | --- |
| `provider` | `pollinations` / `openai` / `google` | `pollinations` |
| `apiBase` | OpenAI 兼容端点 URL | `https://text.pollinations.ai/openai` |
| `model` | 模型名 | `openai` |
| `targetLanguage` | 目标语言 | `简体中文` |
| `chatBatchDelayTicks` | 聊天批量发送间隔(游戏刻) | `30` |
| `maxBatchLines` | 单次请求最多带几条文本 | `8` |
| `requestDelayMs` | 两次请求最小间隔(防免费限流) | `5000` |

> 免费模型(Pollinations 匿名 tier)实测约几秒一单,连发会返回 402/429;模组已内置
> 3 次退避重试(4s/8s/12s)+ 请求节流。若仍频繁失败,可换自定义 OpenAI 兼容接口。
> Google 备用源在国内网络通常无法直连,仅作海外环境备用。

## 技术说明(26.1 适配点)

- 26.1 起原版不再混淆,直接使用 Mojang 官方映射编译;Loom 1.19 新插件免 remap。
- 聊天:26.1 的 `ChatComponent` 把所有消息汇入私有 `addMessage(...)` —— 在此处 mixin 挂钩。
- 告示牌:26.1 渲染改为 RenderState 流水线,所有告示牌文字都经过 `SignText#getRenderMessages` —— 在此整牌替换。
- 书本:`BookViewScreen`(含 `LecternScreen` 子类)注入 `译` 按钮,页面文本经 `BookViewScreen$BookAccess` 读取。
- 参考:实现思路参考了 GitHub 上同类项目 [SimpleTranslate](https://github.com/baokaixina/SimpleTranslate)(缓存/批量翻译)与 [Chatglot](https://github.com/yoima-jp/Chatglot)。

## License

MIT

[English](README.md)
