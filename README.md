# 大肥肠偷人法 (HandJump) 中文拼音输入方案

本输入法适用于注重隐私、具备安卓思维的用户。开发基于 小企鹅输入法 [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) 与 [rime-frost（白霜拼音）](https://github.com/gaboolic/rime-frost) ，支持 **Qwerty全拼 / Suretype双键 / 九键** 三种 Rime 方案。针对小企鹅输入法的设计，主要添加了拼音分词器帮助用户筛选选项。

目前还存在一些毛刺，计划后续进行更新和维修，主要问题包括：
    1. 针对按键滑动点选的支持还不完善
    2. 针对suretype布局和T9的xlit按键映射方案还没有实装——目前是直接裸入输入管道
    3. 按键逻辑还需要优化
    4. 可能存在其它潜在的稳定性问题，目前还没有足够多的实测能够支持观察

开发纯粹基于VibeCoding，主力为OpenCode+DeepSeek V4 Pro和Cursor。如果没有AI恶意植入，应该不会涉及到泄露隐私的问题。

## 目录结构（单一 monorepo）

```
.
├── README.md                 # 本文件
├── ARCHITECTURE.md           # 架构与构建说明（必读）
├── fcitx5-android/          # Android 主工程 + Rime 插件 + HandJump 定制 UI
├── rime-frost/               # 白霜拼音词典与方案（构建时由 CMake 安装）
└── releases/                 # 预编译 arm64 release APK（可直接安装）
    ├── HandJump-main-arm64-release.apk
    └── HandJump-rime-arm64-release.apk
```

## 快速安装（无需自行编译）

1. 先安装 `releases/HandJump-rime-arm64-release.apk`（Rime 插件）
2. 再安装 `releases/HandJump-main-arm64-release.apk`（主程序）
3. 进入大肥肠偷人法
 <img width="384" height="417" alt="0e9a6ff5e6accdb6f473b011259fab23" src="https://github.com/user-attachments/assets/82e01a9f-07cd-4617-8dbc-c300afffde7f" />

4. 在系统设置中启用输入法，并在大肥肠偷人法的“输入法”中删除拼音，改为“中州韵”
 <img width="1080" height="2400" alt="a4689bded234d2ed5d4b284e15883b9e" src="https://github.com/user-attachments/assets/2e2acadb-8f1e-4abf-81a0-83cc085c7d50" />

5. 在主题中勾选启用按键边框
<img width="1080" height="2400" alt="91995ebdfde23a5c74d5a331e49fbac1" src="https://github.com/user-attachments/assets/5e96f313-2cf1-448f-b65c-4f6849acc17c" />

6. 点击地球标，切换到中州韵，
7. 键盘左上方的>键，在弹出的键盘中点击...唤出快速菜单
9. 点击**重载配置**
10. 重启手机
11. 在菜单第二行第一个图表中可以切换按键布局和输入方式（图有误，没有英文模式）
<img width="1080" height="1482" alt="21e8a6d58b037726db09dc0b2310f8b0" src="https://github.com/user-attachments/assets/f4a91ea1-b929-44fa-8705-8ac1b9deb272" />
12. Suffer from 大肥肠偷人法！


## 从源码构建

## 上游、许可和责任豁免请求

| 组件 | 仓库 |
|------|------|
| Android 壳 | https://github.com/fcitx5-android/fcitx5-android |
| 拼音方案/词库 | https://github.com/gaboolic/rime-frost |

本人第一次基于github生态使用和分发开源项目，如有操作不规范之处，还请海涵并指出。

主工程许可见 `fcitx5-android/LICENSE`。发布 APK 仅供体验；二次分发或修改请遵守各上游许可证。

