# 本地合并记录：Aerial 2.15

## 审查后修复：2.15-local.2

2026-11-21：完整版本升级为 `2.15-local.2`（versionCode 10002），Lite 保持不变。以下两项是本次代码修复，尚未在真实电视验证：

- 极米显式 HDMI 播放器在输入源列出后消失或被固件拒绝启动时，控制中心也能回退到原生输入源面板。识别同时检查组件与 action，不误处理普通应用，不重复启动被拒绝的播放器；固定输入源磁贴继续保留回退。
- `cn.formovie.fengos.settings` 纳入设置包识别，系统默认设置指向 AOSP 时，峰米设置磁贴及设置 intent 仍需通过家长 PIN。输入源选择继续不受设置 PIN 阻挡。
- 新增 5 项回归测试，覆盖上述入口、SecurityException、播放器消失、双重失败提示、普通应用不误回退及解锁后才打开设置。两个变体各 334 项单测通过；两个 release 构建成功；完整 lint 各 0 错误、108 警告、1 提示，与上一版逐项比较无新增。
- 新产物在 `local/2.15-local.2-artifacts/`：APK、匹配的 API 28 / 31 `.dm`、manifest、签名及测试报告。版本号、权限、HOME 声明和签名已核验，签名与 local.1 一致。上一版归档保留。脚本和 Lite 未改，本轮未重跑它们的测试。

## 最近 issues 核查（2026-11-21）

通过 GitHub API 查看按更新时间排序的最近 60 条记录（56 个 issue、4 个 PR），另按极米 / TCL 检索，并细读 12 个相关 issue 及评论。下面区分社区复测、维护者说明与本地代码判断，不将其他型号结论套用于 Z7X 或 65V8M Pro。

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
