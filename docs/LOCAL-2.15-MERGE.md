# 本地合并记录：Aerial 2.15

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
