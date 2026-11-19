# 手柄 / TV 模式

面向十英尺观看距离的一套并列界面：进入后整屏以焦点为核心，只用手柄或键盘即可完成浏览、阅读、评论、
收藏与设置。原有触屏 / 鼠标界面保持不变。

## 开启方式

- 设置 → 系统与更新 → 「手柄 / TV 模式」，切换后立即生效（桌面端同样即时，不再需要重启）。
- Android 电视 / 电视盒子（`UI_MODE_TYPE_TELEVISION`）默认开启。
- 桌面可用 `--tv` 启动参数直接进入：`./gradlew :desktopApp:run --args="--tv"`。
- TV 设置页最后一项「退出 TV 模式」会写回同一个偏好并立即切回普通界面。

## 按键

| 动作 | 手柄 | 键盘 |
| --- | --- | --- |
| 移动焦点 / 滚动正文 | 方向键、左摇杆 | 方向键 |
| 确认 | A | Enter / Space |
| 返回上一级 | B | Esc |
| 切换栏目 | L1 / R1 | PageUp / PageDown |
| 打开设置 | Start | Menu |
| 次要动作（保留） | X / Y | — |

提示条始终显示当前页面可用动作。输入文字时方向键用于移动光标，不再移动焦点。

## 首期范围

- 首页 / 关注 / 热榜信息流（大卡片网格，自动分页）。
- 文章、回答、问题、想法详情：正文走 Compose Markdown，右侧操作列提供赞同、评论、收藏与回答切换。
- 评论列表（确认键点赞）。
- 搜索（热搜、历史、结果）。
- 扫码登录、收藏夹与收藏内容、在线历史、通知。
- 精简 TV 设置。

写回答、发想法、私信等编辑类页面不在手柄模式内；未注册的目的地会显示「该功能暂不支持手柄模式」，
而不是空白页。

## 实现要点

- 输入只有一套语义 `TvAction`：Android 手柄 / 遥控器按键以 Compose KeyEvent 到达，桌面键盘走同一映射，
  桌面手柄由 Jamepad（SDL2）在独立线程轮询后汇入同一个动作流。
- 方向移动由 TV 根节点调用 `FocusManager.moveFocus`；正文页显式取消上下方向焦点移动，让方向键滚动正文，
  向右进入操作列。
- 页面只复用既有 ViewModel 与 `rememberZhihuApiEnvironment`，不复制请求逻辑。

## 已知限制

- Android TV 的搜索输入依赖系统输入法；没有可用 IME 的设备只能从热搜和历史里选择。
- 子评论暂未提供独立页面，评论页展示根评论与回复数量。
- 收藏内容是只读列表，新增 / 删除收藏夹仍需普通界面。

## 验证

```bash
./gradlew :shared:jvmTest --tests "com.github.zly2006.zhihu.tv.*"
./gradlew assembleLiteDebug
./gradlew ktlintFormat
```

桌面端跑 `--tv` 后可用方向键 + Enter/Esc 完成「首页 → 详情 → 返回」；Android 端可用
`adb shell input keyevent KEYCODE_DPAD_DOWN KEYCODE_DPAD_CENTER KEYCODE_BACK` 验证同一路径。
