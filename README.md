# Embysic

[中文](#中文) | [English](#english)

<a name="中文"></a>
## 中文

Embysic 是一款专为 Android 打造的高颜值、轻量化 Emby 音乐客户端。它旨在为用户提供极致的音乐播放体验，结合了现代化的 UI 设计与流畅的交互逻辑，让您的 Emby 音乐库焕发新生。

### 📥 下载与安装
- **最新版本**：[v2.14 - 点击从 GitHub Releases 下载](https://github.com/kuangru52/embysic/releases/latest)
- **更新亮点**：封面与歌词智能联动，沉浸式高斯模糊背景同步，性能大幅优化。

### 项目简介
![示例图片](https://images2.imgbox.com/5b/7b/Z6TOtilC_o.jpg)

Embysic 不仅仅是一个播放器，它追求原生般的流畅感与精致的视觉细节。项目采用了 Material Design 3 设计规范，并针对音乐播放场景深度优化了手势操作与界面过渡动画。

### 隐藏功能与交互指南

为了保持界面的简洁，我们设计了一系列直观的“隐藏”交互功能，让高级用户能够更高效地操控音乐：

#### 1. 强制直连模式 (Force Direct Mode)
- **操作方式**：在播放界面连续点击 **“音频质量”** 文本（如 "128kbps" 或 "FLAC"）**5 次**。
- **功能**：强制切换播放模式。当您处于复杂的网络环境或需要原始音质时，此功能可快速切换连接策略，实时生效。

#### 2. 播放定位 (Quick Locate)
- **操作方式**：在播放界面点击顶部的 **“歌曲标题”**。
- **功能**：立即关闭播放器界面并自动跳转定位到当前播放歌曲在音乐库中的位置，方便您查看专辑其他曲目。

#### 3. 底部滑动调节音量 (Volume Swipe)
- **操作方式**：在播放器最底部的触控区域（约 64dp 高度）进行 **左右水平滑动**。
- **功能**：无需呼出系统音量条，直接通过手势调节系统音量。界面中心会显示自定义的 `VolumeDotView` 视觉指示器，提供丝滑的调节反馈。

#### 4. 歌手快速跳转 (Artist Jump)
- **操作方式**：点击播放界面底部的 **“歌手名称”**。
- **功能**：一键跳转至该歌手的专辑列表页面，发现更多相关作品。

#### 5. 歌词与封面切换 (Lyric Sync)
- **操作方式**：点击播放界面中间的 **唱片/磁盘区域**。
- **功能**：在精美的黑胶唱片视图与同步歌词视图之间快速切换。歌词采用智能对齐算法，始终保持当前行处于屏幕黄金分割位。

### 技术亮点与优化

- **毛玻璃底栏 (Glass Dock)**：在 Android 13+ 设备上实现了实时“液态玻璃”模糊效果，底部导航栏视觉深度更丰富。
- **动态边距适配**：采用 `OnApplyWindowInsetsListener` 动态处理状态栏与导航栏高度，移除冗余的 Spacer 视图，布局更高效。
- **智能跑马灯**：标题与歌手名在过长时自动开启跑马灯效果，始终展示完整元数据。
- **性能飞跃**：深度精简布局层级，移除所有冗余的渐变遮罩资源，提升低端机型的渲染性能。

---

## 开发与贡献

如果您对本项目感兴趣，欢迎提交 Issue 或 Pull Request。
---

<a name="english"></a>
## English

Embysic is a high-aesthetic, lightweight Emby music client designed for Android. It aims to provide the ultimate music playback experience by combining modern UI design with smooth interaction logic, bringing your Emby music library to life.

### 📥 Download
- **Latest Version**: [v2.14 - Download from GitHub Releases](https://github.com/kuangru52/embysic/releases/latest)
- **Key Highlights**: Smart cover-lyric synchronization, immersive Gaussian blur background sync, and significant performance optimizations.

### Project Introduction

Embysic is more than just a player; it pursues a native-like smoothness and exquisite visual details. The project adopts Material Design 3 specifications and deeply optimizes gesture operations and interface transition animations specifically for music playback scenarios.

### Hidden Features & Interaction Guide

To keep the interface clean, we have designed a series of intuitive "hidden" interaction features for advanced users:

#### 1. Force Direct Mode
- **How to use**: Tap the **"Audio Quality"** text (e.g., "128kbps" or "FLAC") **5 times** on the player screen.
- **Function**: Forces a toggle of the playback mode. This allows for quick switching of connection strategies when in complex network environments or when original audio quality is required.

#### 2. Quick Locate
- **How to use**: Tap the **"Song Title"** on the player screen.
- **Function**: Immediately dismisses the player and navigates to the song's position in the music library, making it easy to browse other tracks in the album.

#### 3. Volume Swipe
- **How to use**: **Swipe horizontally** on the touch area at the very bottom of the player (approx. 64dp height).
- **Function**: Adjusts system volume via gestures without calling up the system volume bar. A custom `VolumeDotView` visual indicator appears in the center for smooth feedback.

#### 4. Artist Jump
- **How to use**: Tap the **"Artist Name"** at the bottom of the player.
- **Function**: Jumps directly to the artist's album list page to discover more related works.

#### 5. Lyric Sync Toggle
- **How to use**: Tap the **Disk/Center area** of the player.
- **Function**: Toggles between the beautiful vinyl record view and the synchronized lyrics view. Lyrics use a smart alignment algorithm to keep the current line at the screen's golden ratio.

### Technical Highlights & Optimizations

- **Glass Dock**: Implements real-time "Liquid Glass" blur on Android 13+ devices for a richer visual depth in the bottom navigation bar.
- **Dynamic Inset Adaptation**: Uses `OnApplyWindowInsetsListener` to dynamically handle status and navigation bar heights, removing redundant Spacer views for a more efficient layout.
- **Smart Marquee**: Automatically enables marquee effects for long titles and artist names to ensure full metadata visibility.
- **Performance Boost**: Streamlined layout hierarchy and removed all redundant gradient mask resources to improve rendering performance on lower-end devices.

---


**License**
Apache License 2.0
