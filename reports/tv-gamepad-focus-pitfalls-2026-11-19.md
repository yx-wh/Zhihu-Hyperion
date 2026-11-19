# TV / 手柄模式踩坑记录

Date: 2026-11-19
## 1. 手柄动作流捕获旧回调
现象：连续按 L1/R1 卡在热榜/关注。
原因：LaunchedEffect 长期收集手柄 Flow，捕获了首次组合的 onAction。
闭包里的 selectedTab 因此永远停在初始值。
修复：TvInputRoot 用 rememberUpdatedState(onAction)。
切栏时从 selectedTabName 这个 State 重新读取当前栏目。
## 2. popBackStack 的 typed route 查找失败
现象：日志反复出现 Ignoring popBackStack ... not found。
原因：popBackStack<TvRootRoute>() 没有匹配到根路由。
修复：用 graph.startDestinationId 判断和回退，不再按 typed route 查找。

## 3. 重组前 requestFocus 静默失败
现象：切栏后没有任何焦点，方向键和 B 都失效。
原因：新的侧栏项还没挂上 FocusRequester 就请求焦点。
修复：先记录 pendingTabFocus，下一帧再 requestFocus，最多重试 3 帧。

## 4. 空列表导致没有焦点目标
现象：问题详情加载失败后 B 退不回去。
原因：回答列表为空时页面没有可聚焦项。
修复：进入问题页先聚焦评论按钮，回答列表加载后再切到第一条。

## 5. 手柄 A 不经过 Compose 按键管线
现象：十字键能移动，A 无法确认。
原因：手柄动作走 Flow，焦点项的 onKeyEvent 只处理键盘事件。
修复：TvFocusedActionHost 登记当前焦点项，根节点把 Confirm/Secondary 转发给它。

## 6. 方向键被根节点当成焦点移动
现象：文章正文无法上下滚动。
原因：根节点 preview 先消费 Up/Down，moveFocus 又找不到目标。
修复：正文登记 scroll handler，根节点优先滚动，未处理才移动焦点。

## 7. 正文初始焦点被操作列抢走
现象：进入文章后按 Down 不动。
原因：首帧前 requestFocus 失败，焦点落到右侧操作列。
修复：withFrameNanos 后再请求正文焦点。

## 8. 多手柄共用摇杆状态
现象：接两个手柄时，按住摇杆会重复触发移动。
原因：stickDirection 是全局变量，另一只手柄居中会把它清空。
修复：按 controller.index 分开保存摇杆状态。

## 9. L2/R2 是模拟轴而不是按钮
现象：Jamepad 的 ControllerButton 里没有 L2/R2。
修复：读取 TRIGGERLEFT/TRIGGERRIGHT，按 0.5 阈值做按下沿检测。

## 10. Linux 2x 缩放下界面仍按 1x 渲染
原因：X11 的 AWT 不自动读取 GNOME scaling-factor / Xft.dpi。
修复：DesktopUiScale 在 AWT 初始化前检测 GDK_SCALE、gsettings、xrdb。

## 11. 原生扫码登录失败
现象：TV 扫码页显示网页风控但二维码不可用。
修复：TV 登录页直接复用 WebLoginPane，扫描网页内二维码。

## 12. release 包里的 Jamepad native
ProGuard 会裁掉 JNI 按名字查找的类。
修复：keep com.studiohartman.jamepad 和 com.badlogic.gdx.jnigen，并实际启动 release jar 验证。

## 验证方式
```bash
./gradlew :shared:jvmTest :shared:detektMainJvm :shared:detektMainAndroid ktlintCheck
./gradlew :desktopApp:packageReleaseUberJarForCurrentOS
java -jar desktopApp/build/compose/jars/Zhihu-Hyperion-linux-x64-0.38.0-release.jar
```
桌面用真实手柄事件验证 D-pad、A/B、L1/R1、L2/R2；Android 用 adb shell input keyevent 验证焦点和返回。
