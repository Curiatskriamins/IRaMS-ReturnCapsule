# REccc 暂存区

这里存放子智能体产出的**优化草稿**，不参与构建。

## 约定

- 镜像源码路径：`REccc/src/main/java/...` 对应 `src/main/java/...`；
- 每次提交必须更新 `REccc/MANIFEST.md`，逐条写：文件、改动、理由、风险、是否改变行为；
- `src/` 只由 lead 在逻辑复核后合并；
- 合并前必须跑全量 `javac`（`build/verify-parkour`）；
- 任何“看起来更优雅但行为可能变化”的改动，必须在 MANIFEST 里标 `行为变更：是`，默认不合并。
