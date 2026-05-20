# HandJump（大肥肠输入法）

基于 [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android) 与 [rime-frost（白霜拼音）](https://github.com/gaboolic/rime-frost) 合并开发的中文输入法，支持 **全拼 / 双键 / 九键** 三种 Rime 方案，键盘由 Schema 驱动切换。

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

**布局要求**：`rime-frost/` 必须与 `fcitx5-android/` 同级（见 `fcitx5-android/plugin/rime/src/main/cpp/CMakeLists.txt`）。

## 快速安装（无需自行编译）

1. 先安装 `releases/HandJump-rime-arm64-release.apk`（Rime 插件）
2. 再安装 `releases/HandJump-main-arm64-release.apk`（主程序）
3. 在系统设置中启用输入法，进入应用内完成 Rime **重新部署**

## 从源码构建

环境变量与完整步骤见 **[ARCHITECTURE.md](./ARCHITECTURE.md)**。

```powershell
cd fcitx5-android
.\gradlew :app:assembleRelease :plugin:rime:assembleRelease --no-daemon
```

## 上游与许可

| 组件 | 仓库 |
|------|------|
| Android 壳 | https://github.com/fcitx5-android/fcitx5-android |
| 拼音方案/词库 | https://github.com/gaboolic/rime-frost |

主工程许可见 `fcitx5-android/LICENSE`。发布 APK 仅供体验；二次分发请遵守各上游许可证。

## 不包含的内容

为保持仓库可克隆、可构建，本发布包**未包含**：

- `Replicatables/`（早期开发快照，不参与构建）
- `build/`、`.gradle/`、`.cxx/` 等本地构建缓存
- `rime-frost/others/`（与正式构建无关的辅助目录）
