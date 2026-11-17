# 装修记账

个人 / 家庭装修记账 App：只管理一套房子的装修，账本和凭证保存在手机本地，记账无需登录。检查更新时连接 GitHub。

## 1.0.8 / 1.0.9 静态更新清单

修复匿名 GitHub API 被限流而无法检查更新的问题。公开检查与下载改为读取发行版附件 `update.json` 和 APK 文件，不再调用 REST API，也不需要访问令牌。详见 [更新清单与发布约定](docs/UPDATE-1.0.8.md)。

先安装 1.0.8 修正版，再在应用内检查 1.0.9 测试更新；两版继续使用现有签名，可覆盖当前的新签名版本。

## 1.0.7 更新检查测试

本次只将版本递增到 1.0.7 / versionCode 8，沿用 1.0.6 的界面、功能、公开更新方式与签名。

保留手机上的 1.0.6，打开“我的 → 检查更新”，应看到 1.0.7；可继续测试下载和 Android 覆盖安装，完成后页面显示当前版本 1.0.7。

- [1.0.7 发行版](https://github.com/a1070503737-dev/code/releases/tag/v1.0.7)
- [安装包](dist/renovation-ledger-1.0.7-signed.apk)
- [精简源码](dist/renovation-ledger-1.0.7-source.zip)

## 1.0.6 修复检查更新时的 401

仓库当前为公开仓库。新版检查和下载更新优先匿名访问，避免旧内置令牌失效时阻塞公开发行版；只有私有访问需要时才尝试备用授权。

- [下载 1.0.6 已签名 APK](dist/renovation-ledger-1.0.6-signed.apk)
- [下载 1.0.6 精简源码](dist/renovation-ledger-1.0.6-source.zip)
- [问题原因、修复与验证说明](docs/UPDATE-1.0.6.md)

版本为 1.0.6 / versionCode 7，沿用现有签名，可覆盖本次发布的 1.0.3、1.0.4、1.0.5。公开更新无需填写令牌。如果 1.0.5 提示更新授权被拒绝，请先手动安装本次修正版。

## 1.0.5 免配置更新

正式安装包已接入本仓库的共享只读访问，安装后直接打开 **“我的 → 检查更新”**，无需每个人配置令牌。支持查看更新说明、下载进度、取消重试，以及校验后进入 Android 安装界面。

- [下载 1.0.5 已签名 APK](dist/renovation-ledger-1.0.5-signed.apk)
- [下载 1.0.5 精简源码](dist/renovation-ledger-1.0.5-source.zip)
- [共享更新访问与维护说明](docs/UPDATE-1.0.5.md)

沿用 1.0.3、1.0.4 的签名，包名和数据库结构保持一致，版本为 1.0.5 / versionCode 6。可覆盖本次发布的 1.0.3 或 1.0.4。以后更新仍需使用同一签名密钥，并在正式打包时传入专用只读令牌文件。令牌原文不写入源码；内置访问的 APK 按所有者要求分发给用户。

## 1.0.4 应用内更新

新增 **“我的 → 检查更新”**：查看 GitHub 正式发行版，直接下载 APK，显示进度、支持取消与重试，校验后打开 Android 安装界面。

- [下载 1.0.4 已签名 APK](dist/renovation-ledger-1.0.4-signed.apk)
- [下载 1.0.4 精简源码](dist/renovation-ledger-1.0.4-source.zip)
- [更新功能与 GitHub 访问设置](docs/UPDATE-1.0.4.md)

本仓库保持私有。首次检查更新时，需要在应用里配置仅允许本仓库 `Contents: Read-only` 的 GitHub Fine-grained token；令牌在手机上加密保存，不包含在 APK、源码或账本备份中。

1.0.4 沿用 1.0.3 的新签名，包名不变、versionCode 递增到 5，**可以覆盖安装本次发布的 1.0.3**。1.0.3 尚无更新入口，需要先手动安装一次 1.0.4；以后的版本可在应用内下载更新。原始 1.0.2 的临时签名仍无法覆盖。

## 1.0.3 界面与可靠性优化

本分支优化了首页预算总览、浅色/深色主题、账单卡片、待付筛选、记账空状态，以及付款反馈和备份校验。详见 [审查与更新说明](docs/REVIEW-1.0.3.md) 与 [界面截图](docs/UI-PREVIEW.md)。

### 下载 1.0.3

- [已签名 APK，约 3 MB](dist/renovation-ledger-1.0.3-signed.apk)：打开后点击 **Download raw file** 下载。
- [精简源码 ZIP，约 2.6 MB](dist/renovation-ledger-1.0.3-source.zip)：含源码、构建脚本、测试和界面截图，不含 APK、构建缓存或签名私钥。
- [安装与迁移说明](docs/INSTALL-1.0.3.md)、[签名验证记录](docs/SIGNING-VERIFICATION-1.0.3.txt)、[下载文件 SHA-256](dist/SHA256SUMS-1.0.3.txt)。

**1.0.3 已使用新密钥签名，不能直接覆盖原版 1.0.2。** 原签名私钥无法找回，因此本次切换签名；包名仍为 `com.zhuangxiu.ledger`，数据库结构仍为 Room v1，版本为 1.0.3 / 4。旧用户请先将账本备份导出到下载目录并确认保存成功，再卸载旧版、安装新版、恢复备份。

后续更新必须继续使用本次新密钥、相同包名和更大的 versionCode，并用 `tools/verify_update.py` 与本次已签名的 1.0.3 APK 比对。签名密钥与密码已单独交付，不存放在仓库中。

## 原版 1.0.2 下载安装

不想自己编译的话，可以直接下载安装包：[`dist/renovation-ledger-1.0.2-debug.apk`](dist/renovation-ledger-1.0.2-debug.apk)（约 26 MB，点开后按 **Download raw file**）。

- 下载到手机后点击安装，系统会提示“允许安装未知来源的应用”，允许即可。
- 这是**调试版**安装包，用临时的调试签名。后续更新需沿用当时的 `debug.keystore`；换用新密钥无法覆盖安装。请保留现有应用和签名文件，并定期在 App 中导出备份。

## 功能

- **账单列表**：按日期分组，可搜索、按阶段 / 类型 / 付款状态筛选；**左滑删除**（会先确认）；**长按弹出操作菜单**：查看详情、编辑、登记付款、标记为已付清、再记一笔（沿用阶段/类型/商家）、删除
- **记账**：金额、日期、装修阶段、费用类型、商家/工人、备注、付款状态、多张凭证；首页悬浮按钮一键“记一笔”，数字键盘输入金额
- **付款状态**：已付 / 定金 / 待付尾款 / 未付；一笔订单登记总价、已付、剩余，可分多次付款；待付款可设到期日并提前通知
- **预算**：总预算 + 按阶段 / 类型的单独预算；已花、剩余、百分比、进度条；用到 80% 预警、超支标红并通知
- **凭证**：拍照（CameraX）或从系统相册选图，图片保存在 App 私有目录；全屏查看、双指缩放、删除；凭证库按时间浏览
- **统计**：花费占比饼图（按阶段 / 类型）、每月花费柱状图、预算与实际对比，可按时间范围筛选
- **备份恢复**：数据库 + 图片打包成 zip，保存到本地或用系统分享导出；可从备份文件恢复
- **分类管理**：装修阶段、费用类型都可以增删改、排序；首次启动自动写入默认分类
- 奶油白与松绿的家居风界面，支持深色模式；金额统一显示为 `¥1,234.56`

### 几个约定

- 金额一律用 `Long` 按“分”存储，没有浮点误差。
- **“已花”按账单总价统计**（包含还没付清的尾款），因为签了单这笔钱就已经占用预算；**“待付总额”**是所有账单的剩余未付金额之和。
- 付款状态由“总价 + 付款记录”自动推导：付清即变“已付”，付了一部分是“定金”或“待付尾款”，没付是“未付”。
- 已有账单在使用的分类不能删除（可以改名）。

## 技术栈

Kotlin · Jetpack Compose · Material 3 · MVVM + Repository · Hilt · Room · DataStore · WorkManager · CameraX · Coil 3  
图表用 Compose Canvas 自绘（没有引入第三方图表库）。minSdk 26，targetSdk / compileSdk 37。

## 目录结构

```
app/src/main/java/com/zhuangxiu/ledger/
├── LedgerApp.kt / MainActivity.kt   应用入口、唯一的 Activity
├── data/
│   ├── db/            Room：实体、DAO、数据库（含默认分类）
│   └── repository/    账单、分类、预算、设置仓库；图片存储；备份恢复
├── domain/            纯计算逻辑：预算、付款状态、统计
├── di/                Hilt 模块
├── notification/      本地通知、每日提醒任务（WorkManager）
├── util/              金额、日期工具
└── ui/
    ├── theme/         配色、形状
    ├── components/    通用组件、图表
    ├── navigation/    路由、底部导航
    ├── home/ bills/ stats/ mine/      底部导航的 4 个页面
    └── edit/ detail/ viewer/ gallery/ 记账编辑、详情、图片查看、凭证库
app/src/test/          单元测试（预算计算、付款状态、统计、金额、备份、Repository）
app/schemas/           Room 导出的数据库结构，以后升级表结构写迁移时用
```

## 在 Android Studio 里运行

1. 安装最新稳定版 Android Studio（项目使用 Android Gradle Plugin 9.5，需要较新的版本；JDK 用 Studio 自带的即可）。
2. `File > Open`，选择本项目根目录，等待 Gradle 同步完成。首次同步会下载依赖，时间较长。
   如果提示缺少 SDK，按提示安装 **Android SDK Platform 37** 和 **Build-Tools 37**（`Settings > Languages & Frameworks > Android SDK`）。
3. 准备设备：
   - 真机：在手机“开发者选项”里打开 **USB 调试**，用数据线连接电脑并允许调试；
   - 或模拟器：`Tools > Device Manager > Create Virtual Device`，系统镜像选 Android 8.0（API 26）及以上。
4. 顶部工具栏选中 `app` 和你的设备，点击绿色的 **Run ▶**（Shift+F10）。

## 运行单元测试

- Android Studio：右键 `app/src/test` 目录 > `Run 'Tests in ...'`
- 命令行：`./gradlew testDebugUnitTest`（Windows 用 `gradlew.bat`）

测试在电脑上直接运行，不需要手机或模拟器。Repository 测试使用 Robolectric，首次运行会下载一个约 150 MB 的 Android 运行环境。

## 打包 APK

### 调试包（自己装着用最省事）

`Build > Generate App Bundles or APKs > Generate APKs`，或命令行：

```
./gradlew assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`，可以直接拷到手机安装。调试包体积较大（约 26 MB）、运行稍慢。

### 正式包（体积小、速度快，需要签名）

1. `Build > Generate Signed App Bundle or APK…`，选择 **APK**。
2. 为本分支的 1.0.3 提供覆盖更新时，选择单独保存的 `renovation-ledger-release.keystore`，别名为 `renovation-ledger`，按提示输入密码。不要重新创建密钥。
   **务必保管好密钥文件和密码**：以后升级 App 必须用同一个签名，同时保持包名并递增 versionCode。
3. 选择 `release`，点击 **Create**。产物在 `app/release/app-release.apk`（约 3 MB）。

命令行 `./gradlew assembleRelease` 生成的是**未签名**的包（`app-release-unsigned.apk`），不能直接安装，需要按上面的方式签名。
签名文件和密码不要提交到 Git（`.gitignore` 已忽略 `*.jks`）。

## 界面与行为测试

`./gradlew testDebugUnitTest` 会同时执行金额/预算/Repository/备份测试和 Robolectric Compose 界面测试；截图输出在 `app/build/reports/ui/`，包括浅色、深色、空账本、小屏大字体与记账流程。截图中的演示数据仅用于测试。

## 数据与备份说明

- 数据库和图片都在 App 私有目录，卸载 App 会一并删除；系统的自动云备份已关闭（`allowBackup=false`），数据不会离开手机。
- 换手机、重装或升级前，请在 **我的 > 备份与恢复** 里导出备份；在新手机上用“从备份恢复”选择该 zip 文件即可。
- 恢复会覆盖当前全部数据，完成后 App 会自动重启。
- 提醒由后台任务每天早上 9 点左右检查一次；部分手机的省电策略会推迟或拦截后台任务，收不到提醒时请在系统设置里允许本应用自启动 / 后台运行。
