# 本地合并记录：Aerial 2.15 / 2.16

## 合并上游 2.16：2.16-local.1

2026-11-22：将上游 [v2.16](https://github.com/pengchujin/aerial-tv/releases/tag/v2.16)（`22f9ad8`，versionCode 159）合入本地 main，完整版本为 `2.16-local.1`（versionCode 10004）；guard 带 `-guard` 后缀，Lite 为 `1.39-local.1`（41）。合并提交 `195d269`，回退分支 `backup/pre-2.16-20261122`（合并前本地 9 个提交，merge-base `c9a84b1`，即 v2.15）。

上游本次带来五项修复和若干机型记录：

- **#1689 自身升级后顶掉别的 App**：升级后守护的接管只在前台是安装器、应用商店或原厂桌面时进行。本地在 local.3 已用更严格的 `UpdateRecovery.shouldRestore` 实现同一目标，因此保留本地实现。
- **#1707 小米 ASTP0 亮度**：`getAccessoryService` 在槽位 81（槽位 80 变成 `getTvEventLogService`），按返回 binder 的描述符选槽位。`MitvPanel.kt` 与 Lite 共享。
- **#1708 / #1701 电量 0% 或状态未知**：不显示电量，完整版和 Lite 都改。
- **#1677 海尔 YunOS 信号源**：HDMI 走 `com.haier.tv.menusetting/.RootActivity`，带 `isNewSource=1`、`sourceid=<硬件编号>`，仅在该 Activity 导出且无需额外权限时使用。
- **#1646 屏保时钟字距**：首页显示大时钟时，屏保自己的时钟沿用大时钟的字形和字距。
- 文档：创维 8H651_F1P / 7T633_A3F / 9S94_S9A 与 bigfish 的 `filterHomeAlways`（#1518）、海信 MSD6A828 主页键先闪（#1703 #1693 #1696 #1684）、影科 T8 投影（#1706）、东芝 55U3800C（#1687）；新增 `docs/devices/ffalcon-t966d5.md` 的 TCL 65V8M Pro 记录（#1698）。

### 冲突与取舍

只有三处冲突：

1. `app/build.gradle.kts`：上游把 versionCode 改成 159。保留本地高 versionCode 策略，改为 10004，versionName `2.16-local.1`。
2. `lite/build.gradle.kts`：上游把 Lite 改成 `1.39`（41）。versionCode 41 与上游相同，versionName 采用 `1.39-local.1` 以免与上游包混淆。
3. `HomeGuardService.kt` 的更新后接管分支：上游内联了 `frontByUsage()` + `isInstaller()` + `isStockHome()` 判断；本地调 `recoverAfterUpdate(windowsAtSchedule)`，其中 `UpdateRecovery.shouldRestore` 额外检查屏保状态（`interactive`）、解锁状态和调度后才到达的窗口事件，比上游更严格。保留本地调用，删除因此不再被引用的上游 `isInstaller` 辅助函数（`UpdateRecovery.isSystemInstaller` 用 `queryIntentActivities` 查真实系统安装器，语义更强）。

其余 16 个上游文件无冲突，直接合入。本地 9 个提交（#1689/#1694/#1690 实现、版本策略、诊断、PIN、恢复快照等）全部保留。

### 验证结果

| 检查 | 结果 |
| --- | --- |
| standard 单测 | 343 通过，0 失败、0 错误、0 跳过 |
| guard 单测 | 343 通过，0 失败、0 错误、0 跳过 |
| Lite 单测 | 7 通过，0 失败、0 错误、0 跳过 |
| standard / guard 完整 lint | 各 0 错误、108 警告、1 提示 |
| Lite 完整 lint | 0 错误、147 警告 |
| lint 对比 local.3 | 三个包的 issue ID 和消息逐项相同，无新增、无消失 |
| 三个 release APK | 构建成功，签名校验通过 |

本轮跑的是 **release** 单测（`testStandardReleaseUnitTest` / `testGuardReleaseUnitTest` / `testReleaseUnitTest`），即实际出包的变体；上一份归档保存的是 debug 测试树。归档中的单测报告和 `verification.json` 记录的是本轮 release 结果。

三个包签名均为 `cbcc6032a27fbb1bf52f35ef99600e193f966ea9bba65c92db021b6e218ff4c3`，与 `2.15-local.3-artifacts/` 相同，可覆盖安装已装的版本。APK 内 API 28 / 31 的 `.dm` 已提取，manifest 中 versionCode 为 10004、包含 `primary.prof`。standard / guard 已核对 minSdk 21、非 debuggable、HOME 声明差异、DUMP 导出权限、复活服务签名权限。

产物：`local/2.16-local.1-artifacts/`，含三个 APK、`.dm`、manifest、签名日志、release 单测报告、lint 报告和 `verification.json`。复跑核验：`python3 local/verify-2.16-local1.py`。`local/` 不提交。

### 未验证

- 本次只做了构建、单测和静态检查；之后只把 guard 10004 装到 **TCL 65V8M Pro**（ADB 装被 TCL 校验拒绝，走 `tools/tcast-push.py` 多屏互动，装完 `compile -m speed` 为 `speed`；守护 bound、按主页键从 TCL 桌面回到 Aerial、桌面视频壁纸照常起播）。**Z7X 未装**，Lite 未装。
- 上游 #1707 / #1708 / #1677 / #1646 的修复都只有上游自己的记录；合并进本机不代表在这两台电视上验证过。小米亮度问题与 TCL 无关，TCL 无电池。
- 本机 `filterHomeAlways` 实验配置仍在电视上（见 `TCL/设备配置.md`），合并后的包未重新验证该配置下的行为。
- 82 项离线脚本测试本轮未重跑，沿用 local.1 / local.3 的记录。

## 近期 issue 修补：2.15-local.3

2026-11-21：完整版本为 `2.15-local.3`（versionCode 10003）。三项计划已落实到代码并通过本地验证，尚未在 Z7X / 65V8M Pro 上验收：

- **#1689 更新后抢回首页**：延迟回调重新核对亮屏、解锁、Aerial 前台状态和当前前台应用。只接受调度之后的有效 Activity 窗口事件，否则查询已有使用情况权限提供的前台记录；未知前台、视频/HDMI 应用、允许访问的原生桌面都不接管。安装器必须是启用的系统 APK 处理组件。保留安装标记超时和单次消费，不增加权限。
- **#1694 每日航拍切换时序**：屏保可见且原壁纸播放器与插入回调均就绪后才切换；播放器接受影片之后才消费当天名额。失败保留候选，跨日重新判断，同日重入不重复换片。复用已有播放器，不增加轮询或解码器；缓存上限仍为 3，不增加缓存数量或续播开关。此修补覆盖测试确认的时序缺陷，不能认定它是社区重复片段反馈的唯一原因。
- **#1690 输入源图标库**：贯通手机页能力标记、选择接口、状态/预览、桌面渲染和缓存刷新。已固定输入源可借用图标库图片；上传图片优先，库图片下载成功后才替换旧选择，可恢复原生图标。刷新时保留固定输入源的借用文件，取消固定后按原机制回收库文件，不删除用户上传。Lite 不声明该能力，共享手机页在旧服务端隐藏入口。

验证：standard / guard 各 **343 项单测通过**，Lite **7 项通过**，三个 release APK 构建成功。新增 9 项回归覆盖前台恢复、播放器就绪/跨日及输入源图标；Playwright 用实际手机页、模拟 API 验证选择、恢复、失败提示和旧服务端能力门控，无页面异常。它不是电视端网络或实机联调。完整 lint 各 **0 错误、108 警告、1 提示**，Lite **0 错误、147 警告**，issue ID 和消息与前一份归档逐项相同。82 项离线脚本测试未重跑，沿用 local.1 记录。

产物：`local/2.15-local.3-artifacts/`，包含 APK、完整版本匹配的 API 28 / 31 `.dm`、manifest、签名、单测/lint/浏览器日志及 `verification.json`。完整版本核验 minSdk 21、非 debuggable、HOME 差异、诊断和复活服务权限，签名与 local.2 一致；Lite 保持 `1.38-local.1`（41），因共享页面重新构建并归档，签名与上一份 Lite 一致。旧归档保留，`local/` 不提交。

复现 Android 检查可使用下方验证命令；浏览器检查先启动 `python3 -m http.server 8765 --bind 127.0.0.1 --directory app/src/main/assets/web`，再在已安装 Playwright/Chromium 的环境运行 `python tools/browser_test_source_icons.py http://127.0.0.1:8765/`。

未推送、发布、连接或安装到电视。实机验收需覆盖更新后留在视频/HDMI/原生桌面、睡眠与锁屏、跨日屏保、手机改图标后的电视刷新和断网保留旧图标。没有本机 CPU / RSS 测量结果。

## 审查后修复：2.15-local.2

2026-11-21：完整版本升级为 `2.15-local.2`（versionCode 10002），Lite 保持不变。以下两项是本次代码修复，尚未在真实电视验证：

- 极米显式 HDMI 播放器在输入源列出后消失或被固件拒绝启动时，控制中心也能回退到原生输入源面板。识别同时检查组件与 action，不误处理普通应用，不重复启动被拒绝的播放器；固定输入源磁贴继续保留回退。
- `cn.formovie.fengos.settings` 纳入设置包识别，系统默认设置指向 AOSP 时，峰米设置磁贴及设置 intent 仍需通过家长 PIN。输入源选择继续不受设置 PIN 阻挡。
- 新增 5 项回归测试，覆盖上述入口、SecurityException、播放器消失、双重失败提示、普通应用不误回退及解锁后才打开设置。两个变体各 334 项单测通过；两个 release 构建成功；完整 lint 各 0 错误、108 警告、1 提示，与上一版逐项比较无新增。
- 新产物在 `local/2.15-local.2-artifacts/`：APK、匹配的 API 28 / 31 `.dm`、manifest、签名及测试报告。版本号、权限、HOME 声明和签名已核验，签名与 local.1 一致。上一版归档保留。脚本和 Lite 未改，本轮未重跑它们的测试。

## 最近 issues 核查（2026-11-21，local.3 修补前）

通过 GitHub API 查看按更新时间排序的最近 60 条记录（56 个 issue、4 个 PR），另按极米 / TCL 检索，并细读 12 个相关 issue 及评论。本节保留修补前快照，#1689 / #1690 / #1694 的本地进展以上方 local.3 为准。下面区分社区复测、维护者说明与本地代码判断，不将其他型号结论套用于 Z7X 或 65V8M Pro。

| 反馈 | 核查结论及本地影响 |
| --- | --- |
| [#1391 极米 RS Pro 2 冷启动黑屏](https://github.com/pengchujin/aerial-tv/issues/1391) | 2.11 收窄开机窗口事件判断，避免 CellView 阻挡兜底；11 月 20 日维护者依据三轮复测确认修好并关闭。本地已随 2.15 合入。 |
| [#1383 极米 H3 返回后再次拉首页](https://github.com/pengchujin/aerial-tv/issues/1383) | 仍开放；2.13 增加守护自身诊断输出，等待现场日志，不是已确认修复。 |
| [#1377 极米 H3S HOME 记录失效](https://github.com/pengchujin/aerial-tv/issues/1377) | 因十多天没有新反馈而关闭，不能将关闭状态当作修复证明。 |
| [#1483 RS20 Pro Max HDMI](https://github.com/pengchujin/aerial-tv/issues/1483) | 用户确认 1.99 两个 HDMI 一次直达；HDMI 作为壁纸无画面则被归为固件只向自家播放器供帧。该证据不证明旧 Z7X 也能直切或显示 HDMI 壁纸。 |
| [#1676 TCL 85Q10K 没有亮度滑块](https://github.com/pengchujin/aerial-tv/issues/1676) | 维护者说明第三方无法控制该平台背光，使用 TCL 原生图像面板；关闭原因是固件限制。不能据此判断 65V8M Pro 的具体固件行为。 |
| [#371 TCL 待机后唤不醒](https://github.com/pengchujin/aerial-tv/issues/371) | tc8000_ay30a2 用户确认 1.62 在 AerialDream 真正播放后入睡也能唤醒；后续仍有同款用户黑屏报告，不能宣称 TCL 全系解决。 |
| [#1694 航拍总是重复少数片段](https://github.com/pengchujin/aerial-tv/issues/1694) | 新开放建议，无维护者回复。本地 AerialSet.MAX 仍为 3，用户要求缓存数量及续播策略可调，当前未实现。 |
| [#1690 输入源磁贴使用图标库](https://github.com/pengchujin/aerial-tv/issues/1690) | 新开放建议；本地手机页仍用 `!a.source` 排除信号源，当前只可走已有自定义图标路径。 |
| [#1689 更新后抢回首页](https://github.com/pengchujin/aerial-tv/issues/1689) | 新开放问题，无维护者回复。源码核对：HomeGuardService 的更新分支延迟回调只检查 `!aerialResumed` 就执行 goHome，没有核对是否已有其他应用在前台。对应路径仍在本地，应作为后续独立修复；本轮没有修改，也未在真机复现。 |
| [#1668](https://github.com/pengchujin/aerial-tv/issues/1668) / [#1661 峰米光机不亮](https://github.com/pengchujin/aerial-tv/issues/1661) | 2.13 / 2.14 隐藏相关机型的亮度控制并避免写通用亮度值，本地已合入；有用户报告恢复，Q1 Pro 的 2.14 发布回复仍请求复测。该问题与本轮修复的设置 PIN 是两回事。 |

以下为 local.1 合并时的记录。

日期：2026-11-21。将上游 [v2.15](https://github.com/pengchujin/aerial-tv/releases/tag/v2.15)（`c9a84b1`）合入本地 main，完整版本为 `2.15-local.1`，versionCode 10001；guard 带 `-guard` 后缀。Lite 为 `1.38-local.1`，versionCode 41。未推送、发布或安装到电视。

合并前版本是 `1.86-local.1`（`d152094`），回退参考分支 `backup/pre-2.15-20261121`。不将上游文档中的其他设备实测记录视为本次对 Z7X / 65V8M Pro 的验证。

## 合并取舍

- 接入上游截至 2.15 的极米唤醒 HOME 选择框处理、TCL 设置入口、待机屏保行为、竖屏照片适配、图标库和下载续传等改动。
- 保留本地私有诊断文件及 DUMP 权限导出、复活服务签名权限、设置入口家长 PIN、8 MiB 缩略图缓存、照片双边 2560 像素上限、有界日志缓冲与写入队列，以及安装前恢复快照。
- 诊断冲突：上游开机早期日志补写接入私有目录及有界写入器；增加回归测试。
- 极米语义冲突：Android 14 新 GMUI 可用时优先直接切 HDMI；播放器不可用时保留旧设置服务输入源面板。新增两个 API 34 测试，模拟服务包含真实 action 匹配条件。
- 更新冲突：保留上游分段下载、校验和重命名流程，在最终 APK 路径调用旧 Android 安装器的读取权限处理。
- 修正 `VideoWallpaper.kt` 的 Media3 opt-in 声明，消除先前 6 个 lint 错误。测试 JVM 最大堆设为 1 GiB，解决大型图片测试夹具内存不足；不改变电视进程内存配置。
- Lite 的共享源码清单补上本地 `VendorAccess.kt`，解决共享小米面板的编译依赖缺失，不引入 AndroidX。
- 上游脚本测试的假 ADB 补齐本地快照协议；首次保存和重复安装行为仍由状态化恢复测试覆盖。没有连接真实设备。

完整版本沿用本地高 versionCode 策略。应用内仍可能显示上游更新提示，但较低 versionCode 的上游 APK 会被拒绝，避免直接覆盖本地补丁。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| standard 单测 | 329 通过，0 失败、跳过 |
| guard 单测 | 329 通过，0 失败、跳过 |
| Lite 单测 | 7 通过，0 失败、跳过 |
| 本地脚本测试 | 40 通过 |
| 上游脚本测试 | 42 通过 |
| 三个 release APK | 构建成功，签名校验通过 |
| standard / guard 完整 lint | 各 0 错误、108 警告、1 提示 |
| Lite 完整 lint | 0 错误、147 警告 |

完整版本警告主要是 UseKtx（53）、PrivateApi（11）、StaticFieldLeak（11）、ApplySharedPref（7）、UnusedResources（7）等，未全局屏蔽。`LegacyUpdateTls` 的自定义 TrustManager 提醒仍保留：源码先调用系统证书验证，再尝试补充根证书的标准验证，并保留默认主机名校验；本次不是完整 TLS 安全审计。

standard / guard 已核对版本号、minSdk 21、非 debuggable、HOME 声明差异、DUMP 导出权限及复活服务签名权限；签名与之前本地归档一致。APK 内 API 28 / 31 的 `.dm` 已提取，核对包名和 versionCode 与 APK 一致。官方 APK 是否可覆盖安装，仍取决于已安装包的实际签名。

归档目录：`local/2.15-local.1-artifacts/`，包含 APK、匹配的 `.dm`、签名报告、manifest、单测报告、lint 报告和 `verification.json`。该目录不提交到 Git。

复现命令（先 `source local/dev-env.sh`）：

```sh
./gradlew :app:testStandardDebugUnitTest :app:testGuardDebugUnitTest :app:lintStandardRelease :app:lintGuardRelease :app:assembleStandardRelease :app:assembleGuardRelease :lite:assembleRelease :lite:testReleaseUnitTest :lite:lintRelease --continue --console=plain
python3 -m unittest discover -s tools/tests -p 'test_*.py' -v
python3 -m unittest discover -s tools -p 'test_*.py' -v
```

## 待实机验收

未连接 Z7X 或 65V8M Pro。仍需确认当前固件、安装签名和权限状态，验证 HOME、HDMI、亮度、PIN、屏保、待机唤醒及恢复流程。单测和构建通过不能证明 TCL 待机黑屏已在这台机器上解决，也没有本机 RSS / CPU 测量结果。
