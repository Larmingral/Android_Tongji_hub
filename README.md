# TJ Hub (`Android Tongji Hub`)

> **Copyright © 2026 [Larmingral](https://github.com/Larmingral)** · 基于 [MIT License](LICENSE) 开源

面向同济大学校园日常使用的原生 Jetpack Compose Android 应用，集**全景日历、Canvas 课程作业、同济校园邮箱、同济统一身份认证免登与教务课表/通知/成绩**于一体。

## 原创特性与功能概览

1. **全景日历（月视图 / 周视图）**
   - 月视图方框格子直接渲染彩色日程条与单行小字标题，支持左右滑动切换月份。
   - 7 天纵向周视图卡片流，支持左右滑动切换周次，点击日期弹出详情与增删改操作，本地 SQLite 持久化存储。
2. **同济统一身份认证加密免登（Android Keystore）**
   - 本地 `AndroidKeyStore`（`AES/GCM/NoPadding` 256-bit 硬件密钥）加密保存一次学号与密码，教务与 Canvas 共享。
   - 会话过期时自动后台离屏刷新 `iam.tongji.edu.cn` 登录态并重试请求；仅在需验证码/短信二次验证时按需弹出网页验证并在成功后自动返回。
3. **Canvas 原生看板（`canvas.tongji.edu.cn`）**
   - 免配置易失效 Token，直接复用同济统一认证会话 Cookie 请求 REST API，彻底规避学校短期 Token 401 失效问题。
   - 任务按**待完成 DDL**、**已完成 / 已提交（可收起）**、**无截止日期任务（可收起）**三栏展示，有截止日期任务按距离当天绝对天数从近到远排序；支持一键将 DDL 同步到日历、查看课程详情与下载打开课件。
4. **同济教务原生中心（`1.tongji.edu.cn`）**
   - **独立课表**：支持“学年下拉 + 学期二选一”快捷筛选与纯网格高密度课表视图。
   - **通知公告**：透明自适应 `WebView` 完整保留原网页加粗、标红、字号、表格排版及可点击超链接，并按同济 `obsfile/downloadfile`（AES-CBC 加密 `objectkey`）协议下载并打开附件。
   - **考试与成绩**：原生查询考试安排、总均绩及各学期成绩详情。
5. **同济校园邮箱原生客户端（IMAP SSL）**
   - 原生收件箱、图文混排正文阅读（支持 `cid:` / Base64 / 外链内嵌图片）、附件下载打开与一键加入日历。

## 环境与构建

- Android Gradle Plugin `9.0.1` / Gradle `9.1.0` / Kotlin `2.3.20`
- `compileSdk = 37` / `targetSdk = 37` / `minSdk = 26` / JDK 17+
- 打开项目后在 Android Studio 自动生成 `local.properties` 即可编译运行。

## 隐私与安全声明

- 本仓库不包含任何个人账号、学号、密码、Cookie 或邮箱客户端专用密码。
- 所有用户凭证均仅在手机本地加密存储，仅直接与同济大学官方域名（`iam.tongji.edu.cn`、`1.tongji.edu.cn`、`canvas.tongji.edu.cn`、`mail.tongji.edu.cn`）通信。

## 自动化构建与发布（GitHub Actions）

仓库已配置 `.github/workflows/ci.yml`（推送自动运行单元测试）与 `.github/workflows/release.yml`（推送 `v*` 标签自动打包 R8 优化的签名 Release APK 并发布到 GitHub Releases，同时输出签名证书指纹）。
请在 GitHub 仓库 `Settings -> Secrets and variables -> Actions` 配置以下 4 个 Secrets：
- `RELEASE_KEYSTORE_BASE64`（本地 `.jks` 签名密钥库的 Base64 编码）
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

## 安装说明与侧载验证

- **应用包名**：`io.github.larmingral.tjhub`（若手机曾安装早期测试包名 `com.example.campushub` 的旧版本，请先卸载再安装新版）。
- **校验安装包完整性**：每次 Release 均附带 `.sha256` 校验文件，安装前可核对哈希值确认未被篡改。
- **关于国产系统“开发者身份未验证 / 签名来源不明”提示**：
  - Release 包已启用正式密钥签名（v1/v2/v3）与 R8 代码压缩；
  - 手机弹出该提示属于移动智能终端生态联盟 / Google 推行的 **ADV 侧载开发者实名校验**：
    - **普通用户安装**：核对从官方 GitHub Releases 下载且 SHA-256 一致后，按系统引导确认继续安装即可（电脑连接 `adb install -r TJ-Hub-v*.apk` 可免冷却直接安装）。
    - **维护者非上架消警**：可在小米澎湃 OS / OPPO / vivo 开发者平台完成个人实名后，使用后台的 **“侧载应用认证 / 空包签名认领”** 入口绑定包名 `io.github.larmingral.tjhub` 与当前签名证书（可在 Release Actions 日志摘要复制 `apksigner` 输出指纹），无需上架应用商店即可在联盟机型互认消警。

## 知识产权与版权归属

- **原创作者 / 著作权人**：`Larmingral`
- **官方代码仓库**：<https://github.com/Larmingral/Android_Tongji_hub>
- 本项目的整体交互设计、聚合工作流与原生实现由作者原创构思编写，依据 [MIT License](LICENSE) 开源；任何二次分发、修改或借鉴需保留原作者署名及上述版权声明。
