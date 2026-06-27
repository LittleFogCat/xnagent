# UI

本目录说明 XNAgent 的页面结构、导航、主题与设计 token。UI 改动请同步更新本目录的对应文档。

## 文档列表

| 文档 | 适用场景 | 是否需读 |
| --- | --- | --- |
| [navigation.md](./navigation.md) | 三个顶级页面的切换关系、抽屉、登录保护 | 任何导航 / 路由相关问题 |
| [design-system.md](./design-system.md) | 颜色、字体、间距、形状、组件规范 | 修改颜色 / 字体 / 间距前 |

## 阅读顺序

1. 先读 [navigation.md](./navigation.md) 了解页面间关系；
2. 再读 [design-system.md](./design-system.md) 了解可复用的颜色 / 间距 / 组件；
3. 修改具体页面时，结合 [`../2-convention/coding.md`](../2-convention/coding.md) 的 Compose 规范。

## 顶级页面

| 页面 | 路径 | 入口 |
| --- | --- | --- |
| 首页（聊天） | `ui/screen/home/HomeScreen.kt` | 启动后默认页（已登录 / 游客） |
| 登录 / 注册 / 游客 | `ui/screen/login/LoginScreen.kt` | 未登录 / 抽屉 / 游客达到消息上限 |
| 设置 | `ui/screen/settings/SettingsScreen.kt` | 首页抽屉内「设置」 |
