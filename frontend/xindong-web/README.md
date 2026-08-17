# Xindong Station Web Frontend

Vue 3 + Vite + Vant 4 + Pinia + TypeScript

## 启动

```bash
cd web
npm install   # 或 pnpm i / yarn
npm run dev   # http://localhost:5173
npm run build # 构建产物到 dist/
```

## 5个底部Tab
| Tab | 路由 | 模块 |
|-----|------|------|
| 首页 | /app/home | M02首屏9模块聚合 |
| 记录 | /app/record | M03心情 / M04纪念日 / M05日记 / M09清单 |
| 情书 | /app/letters | M06情书 + 时光胶囊(🔴AES红线3) |
| 互动 | /app/interactive | M10默契 / M11破冰 / M08愿望商城 |
| 我的 | /app/settings | M12周报 / 冷静模式 / 主题 / 邀请码 / 退出 |

## 接口层约定
- `src/api/request.ts` 统一拦截器：Bearer Token注入 / code段位处理 / 401跳登录 / Toast/Dialog分级
- 后端43接口URL路径与NestJS版本100%兼容