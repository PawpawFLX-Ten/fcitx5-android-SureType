# HandJump V3 大肥肠方案 — 架构与施工手册

> 从零在 fcitx5-android (commit `4fcd4361`) + rime-frost 上开发三模式中文输入法。
> **核心原则**：Schema 驱动键盘。PinyinBar 数据驱动。
>
> **路线 A（当前）**：三个独立 Rime 方案（`rime_frost` / `rime_frost_suretype` / `rime_frost_t9`），方案菜单切换 schema，键盘壳由 `InputModeRegistry` + `schemaId` 跟随。双键 **仅** 使用 Rime xlit（`rime_frost_suretype.schema.yaml`），不在 Kotlin 重复映射。
>
> **Rime 部署**：构建时 `plugin/rime` CMake 将 `HandJump V3/rime-frost/` 全量（排除 `others/`）安装到 `usr/share/rime-data/`，再以 `fcitx5-android/assets/rime-frost/*.yaml` 覆盖 HandJump 定制 schema；最终以 `plugin/rime/src/main/cpp/default.yaml`（仅三项 `schema_list`）为准。

---

## 0. 核心数据流

```
按键 → Keyboard.onAction → FcitxKeyAction → fcitx5 → Rime Engine
  ↓
speller → prism lookup → table_translator
  ↓ spelling_hints:50 → comment = "ni hao"
  ↓
comment_format → ［ni hao］ → corrector.lua → keep_comments=true → "ni hao"
  ↓
C++ JNI → PagedCandidateEvent → Kotlin
  ├── Candidate.text  → stripRimeComment(comment) → 候选栏（纯净，无注音）
  └── Candidate.comment → normalizePinyinComment → groups() → PinyinBar（分组 chip）
```

**关键**：comment 字段**仅用于 PinyinBar 分组**，不在候选文字上显示。`HorizontalCandidateComponent` 在 PinyinBar 模式下必须被绕过。

---

## 1. 环境

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
$env:ANDROID_HOME = "E:\Android"
$env:ECM_DIR = "E:/msys64/ucrt64/share/ECM/cmake"
$env:PATH = "E:\msys64\ucrt64\bin;$env:JAVA_HOME\bin;$env:PATH"
```

**Replicatables/** — 仅作历史快照，**不参与构建**（见 `Replicatables/README.md`）。请以 `rime-frost/` + `assets/rime-frost/` 为准。

**调试**：Debug 包可用 `HandJumpDiagnostics`（`adb logcat -s HandJump`）。

---

## 2. Rime Schema 基础

**目标**：在 QWERTY 上确保 comment 链路正常。

### 2.1 rime_frost.schema.yaml

基于上游，修改 translator 段：

```yaml
translator:
  dictionary: rime_frost
  spelling_hints: 50           # C2
  always_show_comments: true   # C3
  keep_comments: true          # C1：corrector.lua 不清空
  comment_format:
    - xform/^/［/
    - xform/$/］/
```

speller 保持纯全拼 + 模糊音，**无 xlit、无 T9 derive**。

### 2.2 default.yaml

由插件内 [`plugin/rime/src/main/cpp/default.yaml`](fcitx5-android/plugin/rime/src/main/cpp/default.yaml) 提供（`config_version: '0.61'`，仅三项 `schema_list`）。勿依赖上游 `rime-frost/default.yaml` 的长列表。

### 验证

```powershell
.\gradlew :plugin:rime:assembleDebug --no-daemon
# 部署 + 清除数据 → QWERTY 输入 → 候选应带拼音注音（临时，Phase 3 后消除）
```

---

## 3. PinyinBar

**目标**：候选栏上方蓝色分组条。**无 pinyinGroupingActive 标志**——纯数据驱动。

### 3.1 文件处理

从 Replicatables 复制：

```powershell
copy Replicatables\PinyinCandidateState.kt app\...\candidates\pinyin\
copy Replicatables\PinyinBarComponent.kt     app\...\candidates\pinyin\
copy Replicatables\PinyinCandidateController.kt app\...\candidates\pinyin\
```

### 3.2 PinyinBarComponent.kt — 清理

删除所有 `SureTypeDiagnostics.log(...)`（约 5 处）。其他不动。

### 3.3 PinyinCandidateController.kt — 简化

**删除**（不再需要的激活逻辑）：
```kotlin
private var pinyinGroupingActive = false
private var lastModeId: InputModeId? = null
```

**简化 `onImeUpdate`**：
```kotlin
override fun onImeUpdate(ime: InputMethodEntry) {
    // 不做激活判断。PinyinBar 由数据驱动。
}
```

**`onPagedCandidateUpdate`** — 仅在 `InputModeRegistry.pinyinGroupingEnabled` 为真时处理（与水平候选分流一致）：
```kotlin
fun onPagedCandidateUpdate(data: PagedCandidateEvent.Data) {
    if (!handJumpPinyinBarEnabled(ime)) return
    state = state.copy(
        allCandidates = data.candidates.mapIndexed { index, candidate ->
            PinyinCandidate(
                text = candidate.text.stripRimeComment(candidate.comment),  // ← 净化
                comment = candidate.comment,
                originalIndex = index
            )
        }
    )
    render()
}
```

**简化 `onStartInput`**：
```kotlin
override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags) {
    state = PinyinCandidateState()
    clearHorizontalCandidates()
}
```

### 3.4 HorizontalCandidateComponent.kt — 分流

`PagedCandidateEvent` 和 `CandidateListEvent` 都会触发候选更新。PinyinBar 模式下，`CandidateListEvent` 必须被忽略（它的文字内嵌 comment）。

```kotlin
// 在 onCandidateUpdate(data: CandidateListEvent.Data) 中：
override fun onCandidateUpdate(data: CandidateListEvent.Data) {
    // PinyinBar 模式下，候选由 PinyinCandidateController 通过 PagedCandidateEvent 管理
    if (pinyinBarActive()) {        // ← 新增判断
        if (data.candidates.isEmpty()) refreshExpanded(0)
        return                     // ← 跳过，不污染候选栏
    }
    adapter.updateCandidates(data.candidates, data.total)
    // ...
}

private fun pinyinBarActive(): Boolean {
    val ime = fcitx.runImmediately { inputMethodEntryCached }
    if (ime.isFcitxPlaceholder(context)) return false
    return InputModeRegistry.modeForSubModeNameOrSchemaId(ime.subMode.name, ime.schemaId)
        ?.pinyinGroupingEnabled == true
}
```

### 3.5 InputView.kt — 注册 + 布局

组件注册（`setupScope` 中）：
```kotlin
scope += pinyinBar
scope += pinyinCandidateController
```

布局（PinyinBar 在 KawaiiBar 上方）：
```kotlin
add(pinyinBar.view, lParams(matchParent, dp(38)) {
    topOfParent(); centerHorizontally()
})
add(kawaiiBar.view, lParams(matchParent, dp(32)) {
    below(pinyinBar.view); centerHorizontally()
})
```

### 验证

QWERTY 输入 "nihao" → 候选栏文字纯净+ 上方蓝色 PinyinBar 芯片 "ni hao  5"。

---

## 4. 键盘布局

**目标**：注册 SureType 14键 和 T9 9键。

### 4.1 KeyDef.kt — 添加类型

在 `Appearance` 中添加 `HorizontalSuretype`，在 `Behavior` 中添加 `SwipeLeft/Right/Up/Down`，在 `Popup` 中添加 `DirectionalPreview`。

### 4.2 KeyDefPreset.kt — 添加 SuretypeKey

添加 `SuretypeKey` 类（主/副字母 + 可选 4 方向符号）。

### 4.3 BaseKeyboard.kt — 合并手势

在 `createKeyView()` 中添加 `HorizontalSuretype → HorizontalSuretypeKeyView` 分支。添加 4 方向滑动手势的检测和分发。

### 4.4 KeyView.kt — 添加 HorizontalSuretypeKeyView

渲染双字母纵向排列按键。

### 4.5 复制 + 注册键盘

```powershell
copy Replicatables\SuretypeKeyboard.kt app\...\keyboard\
copy Replicatables\T9Keyboard.kt       app\...\keyboard\
```

`KeyboardWindow.kt` — 注册：
```kotlin
private val keyboards by lazy {
    hashMapOf(
        TextKeyboard.Name to TextKeyboard(context, theme),
        SuretypeKeyboard.Name to SuretypeKeyboard(context, theme),
        T9Keyboard.Name to T9Keyboard(context, theme),
        // ...
    )
}
```

---

## 5. Rime Schema 扩展

**目标**：为 SureType 和 T9 各创建一个独立 Schema + Prism。

### 5.1 rime_frost_suretype.schema.yaml

```yaml
__include: rime_frost.schema.yaml:/
schema:
  schema_id: rime_frost_suretype
  name: 白霜双键
speller:
  algebra:
    - xlit/qwertyuiopasdfghjklzxcvbnm/qqeettuuooaaddggjjlzzccbbm/
    - xlit/QWERTYUIOPASDFGHJKLZXCVBNM/QQEETTUUOOAADDGGJJLZZCCBBM/
    # 以下模糊音 derive 与 rime_frost 相同
translator:
  dictionary: rime_frost
  prism: rime_frost_suretype
  spelling_hints: 50
  always_show_comments: true
  keep_comments: true
```

### 5.2 rime_frost_t9.schema.yaml

```yaml
__include: rime_frost.schema.yaml:/
schema:
  schema_id: rime_frost_t9
  name: 白霜九键
speller:
  alphabet: zyxwvutsrqponmlkjihgfedcbaZYXWVUTSRQPONMLKJIHGFEDCBA9876543210=`/
  algebra:
    - derive/[abc]/2/
    - derive/[def]/3/
    - derive/[hgi]/4/
    - derive/[jkl]/5/
    - derive/[omn]/6/
    - derive/[pqrs]/7/
    - derive/[tuv]/8/
    - derive/[wxyz]/9/
    # 以下模糊音 derive 与 rime_frost 相同
translator:
  dictionary: rime_frost
  prism: rime_frost_t9
  spelling_hints: 50
  always_show_comments: true
  keep_comments: true
```

### 5.3 default.yaml — 更新

```yaml
schema_list:
  - schema: rime_frost
  - schema: rime_frost_suretype
  - schema: rime_frost_t9
```

### 5.4 CMakeLists.txt — 部署

见 [`plugin/rime/src/main/cpp/CMakeLists.txt`](fcitx5-android/plugin/rime/src/main/cpp/CMakeLists.txt)：`install(DIRECTORY …/rime-frost/)` + `assets/rime-frost/` 覆盖 + `default.yaml`。

### 验证

`Ctrl+`` → 方案选单显示 [白霜拼音] [白霜双键] [白霜九键]。

---

## 6. 模式切换

**目标**：方案选单切换 Schema 时，键盘布局自动跟随。

### 6.1 InputModeRegistry.kt

```kotlin
enum class InputModeId { RIME_FROST_QWERTY, RIME_FROST_SURETYPE, RIME_FROST_T9 }

data class InputMode(
    val id: InputModeId, val schemaId: String, val subModeName: String,
    val userLabel: String, val keyboardName: String,
    val pinyinGroupingEnabled: Boolean = false
)

object InputModeRegistry {
    val orderedModes = listOf(
        InputMode(RIME_FROST_QWERTY,   "rime_frost",          "白霜拼音", "白霜拼音", TextKeyboard.Name,      true),
        InputMode(RIME_FROST_SURETYPE, "rime_frost_suretype", "白霜双键", "白霜双键", SuretypeKeyboard.Name, true),
        InputMode(RIME_FROST_T9,       "rime_frost_t9",       "白霜九键", "白霜九键", T9Keyboard.Name,       true)
    )

    private val bySubModeName = orderedModes.associateBy { it.subModeName }
    private val bySchemaId     = orderedModes.associateBy { it.schemaId }

    fun modeForSubModeNameOrSchemaId(subModeName: String, schemaId: String?): InputMode? =
        bySubModeName[subModeName] ?: schemaId?.let { bySchemaId[it] }
    // schemaId 来自 JNI InputMethodEntry（Rime currentSchema），ascii 下 subMode 可能为 Latin Mode
}
```

### 6.2 KeyboardWindow.kt — 联动

- `onImeUpdate`：按 `schemaId` / `subMode` 换壳。
- `switchLayout`：凡目标为 `TextKeyboard.Name`（数字键盘/符号页上的「ABC」），会解析为 `activeAlphabetKeyboard()`，避免壳与当前 schema 脱节。

### 验证

`Ctrl+`` 选不同方案 → 键盘自动切换。空格栏显示对应模式名。

---

## 7. 集成

```powershell
.\gradlew :app:assembleDebug :plugin:rime:assembleDebug --no-daemon
# 清除设备 Rime 数据
adb install -r app\build\outputs\apk\debug\org.fcitx.fcitx5.android-*-debug.apk
adb install -r plugin\rime\build\outputs\apk\debug\org.fcitx.fcitx5.android.plugin.rime-*-debug.apk
```

| # | 验证 | 预期 |
|---|------|------|
| 1 | QWERTY "nihao" | 候选**无注音** + 上方 PinyinBar 芯片 |
| 2 | SureType BN→b, UI→u | 同上 |
| 3 | T9 Tap "64" | 同上 |
| 4 | `Ctrl+`` → 选不同方案 | 键盘布局自动跟随 |
| 5 | PinyinBar | 三种模式下持续显示，无闪烁 |
| 6 | Globe 键 | 切到系统英文再切回，Schema 不变 |

---

## 附录：文件变更总览

| 文件 | 操作 |
|------|------|
| `rime_frost.schema.yaml` | 修改 translator 段（spelling_hints, keep_comments） |
| `rime_frost_suretype.schema.yaml` | 复制 + 确认 speller=xlit |
| `rime_frost_t9_suretype.schema.yaml` | 复制 + 确认 speller=derive |
| `rime_frost.dict.yaml` | 复制（共享词典） |
| `plugin/.../default.yaml` | schema_list 三项 |
| `plugin/.../CMakeLists.txt` | 添加 suretype + t9 install |
| `KeyDef.kt` | 添加 HorizontalSuretype + Swipe* + DirectionalPreview |
| `KeyDefPreset.kt` | 添加 SuretypeKey |
| `KeyAction.kt` | 确认 DirectKeyAction 存在 |
| `BaseKeyboard.kt` | 合并 HorizontalSuretypeKeyView + 方向手势 |
| `KeyView.kt` | 添加 HorizontalSuretypeKeyView |
| `SuretypeKeyboard.kt` | 复制 |
| `T9Keyboard.kt` | 复制 |
| `KeyboardWindow.kt` | 注册键盘 + onImeUpdate 联动 |
| `InputModeRegistry.kt` | 新建 |
| `PinyinCandidateState.kt` | 复制（不动） |
| `PinyinBarComponent.kt` | 复制 + 删除 SureTypeDiagnostics |
| `PinyinCandidateController.kt` | 复制 + 删除激活逻辑 + 删除日志 |
| `HorizontalCandidateComponent.kt` | 添加 pinyinBarActive() 分流 |
| `InputView.kt` | 注册 pinyinBar/pinyinController + 布局 |
| `corrector.lua` / `t9_preedit.lua` | 复制 |
