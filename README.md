# CampusHub (Android Tongji Hub)

面向同济大学校园日常使用的 Jetpack Compose Android 应用，聚合日程规划、Canvas 课程作业、校园邮箱与教务信息入口。

## 功能概览

1. **全景日历（月视图 / 周视图）**
   - 月视图方框格子直接渲染彩色日程条与单行小字标题，支持左右滑动切换月份。
   - 7 天纵向周视图卡片流，支持左右滑动切换周次，点击日期弹出详情与增删改操作，本地 SQLite 持久化存储。
2. **Canvas 原生客户端（`canvas.tongji.edu.cn`）**
   - 原生看板展示待完成与已提交（默认折叠）任务，支持一键将未完成与已完成 DDL 同步到日历。
   - 原生课程详情页（公告 / 作业 / 章节与课件下载打开）及原生作业详情页（描述、截止时间、提交状态、得分与评语），并保留持久化免登 WebView 兜底。
3. **同济校园邮箱原生客户端（IMAP SSL）**
   - 原生收件箱、图文混排正文阅读（直接渲染 `cid:` / Base64 / 外链内嵌图片，不与普通附件混杂）、附件下载打开与一键加入日历。
4. **同济教务入口与结构探测（`1.tongji.edu.cn`）**
   - 保留统一身份认证持久化登录态，内置只读结构探测器以逐步将通知公告、独立课表、考试安排、总成绩迁移为纯原生界面。

## 环境与构建

- Android Gradle Plugin `9.0.1` / Gradle `9.1.0` / Kotlin `2.3.20`
- `compileSdk = 37` / `targetSdk = 37` / `minSdk = 26` / JDK 17+
- 打开项目后请在 Android Studio 配置好 `local.properties`（自动生成）即可编译运行。

## 使用与隐私说明

- 本仓库不包含任何个人账号、学号、Canvas Access Token 或邮箱客户端专用密码。
- 运行后在 App 内自行填写：
  - **Canvas**：在 `canvas.tongji.edu.cn` 的 `账号 -> 设置 -> 已批准的集成` 生成 Personal Access Token。
  - **邮箱**：在 `mail.tongji.edu.cn` 的 `设置 -> 安全设置 -> 客户端专用密码` 生成专用密码（IMAP 服务器 `imap.tongji.edu.cn`，端口 `993` SSL）。
