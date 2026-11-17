# 1.0.8 / 1.0.9 静态更新清单

第二段录屏显示，1.0.6 检查更新时提示“GitHub 暂时限制了请求，请稍后重试”。此提示对应 API 限流响应（429，或带限流标记的 403）。匿名 REST 请求的额度按出口 IP 共享，共享网络或代理可能加大触发概率；录屏本身不能确定具体网络出口和剩余额度。

## 修复

公开更新不再调用 `api.github.com`。检查版本只读取同一个仓库的正式发行版附件：

```
https://github.com/a1070503737-dev/code/releases/latest/download/update.json
```

清单包含版本号、versionCode、固定 APK 文件名、大小、SHA-256 和更新说明。应用按受信任仓库、版本号和固定文件名构造下载地址：

```
https://github.com/a1070503737-dev/code/releases/download/v版本号/renovation-ledger-版本号-signed.apk
```

检查与下载均使用普通发行版文件链接，不消耗 REST API 的匿名请求额度，不使用内置令牌。GitHub 文件服务本身仍可能受网络、可用性或服务限制影响，不能保证所有网络都可达。

继续检查 SHA-256、大小、包名、版本、minSdk 和签名；另增加 APK 的 versionCode 必须与清单完全一致的检查。清单不能指定任意下载域名，附件重定向仅允许 GitHub 的 HTTPS 地址。

## 试用

- 1.0.8 / versionCode 9 是修正版，需要先手动覆盖安装一次；1.0.6、1.0.7 的检查代码尚不支持静态清单。
- 1.0.9 / versionCode 10 使用相同的修复代码，仅递增版本号，作为后续测试更新。
- 手机装好 1.0.8 后，在“我的 → 检查更新”应看到 1.0.9，可继续测试下载、校验与系统覆盖安装。
- 所有这些版本继续使用 1.0.3 起的同一签名，包名与 Room v1 不变。真实手机的完整流程仍需试用确认。

## 后续发版必须附带 update.json

先构建并签名，再从真实 APK 生成清单，不能只改 JSON 的版本号：

```sh
python3 tools/create_update_manifest.py dist/renovation-ledger-1.0.9-signed.apk --notes-file release-notes.txt --output dist/update.json
```

脚本先验证 APK 签名，读取实际包名和版本，再计算大小和 SHA-256。将 APK、update.json、源码 ZIP 和校验文件一起上传到发行版草稿，核对无误后设为正式 Latest。不得先发布再补更新文件，以免客户端读到未就绪的版本。

清单 schemaVersion 固定为 1；APK 文件名必须为 `renovation-ledger-版本号-signed.apk`；版本号仅允许三个数字段，versionCode 必须递增。后续每个 Latest 发行版都必须保留 `update.json` 附件。

## 验证范围

回归测试模拟 REST API 配额耗尽时仍能通过发行版文件读取版本、读取和下载均不访问 REST API、不读取或发送令牌、拒绝损坏或过大的清单、固定下载域名以及精确核对 versionCode。云端网络可能提供额外身份，不能把云端访问成功当作手机网络必定可用或凭据有效的证明。

87 项自动化测试通过，0 失败、0 错误、0 跳过；其中 6 项新增回归覆盖静态清单和直接 APK 下载。

发布后已通过 `releases/latest/download/update.json` 实际读取 1.0.8 清单并直接下载 APK，SHA-256 一致。请求主机仅为 `github.com` 和 `release-assets.githubusercontent.com`，未请求 REST API、客户端未发送授权。此检查仍不等同于真实手机上的网络与安装测试。

## 1.0.8 交付校验

正式构建与签名验证通过；包名不变、版本号递增、签名与上一版一致。APK 和静态清单的版本、大小、SHA-256 一致，公开包不含令牌。[签名记录](SIGNING-VERIFICATION-1.0.8.txt)。

APK SHA-256：`94702759bad04fc12936dc0fadd03505eda3657f9879e4469511b98439a382b5`
