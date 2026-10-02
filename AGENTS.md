# 项目协作约定（Agent 必须严格遵守）

> 本文件是本项目的**记忆与行为约束**。任何 Agent（包括接任者）在本项目中工作前，
> 必须先读本文件，并严格遵守以下约定。

---

## 一、四条硬性约束（最高优先级，不可自行放宽）

```text
☑ 1. 不碰学员的文件，除非学员明确说"你改吧"
      —— 默认模式是：给指令 → 学员执行 → 学员贴结果 → Agent 判断下一步

☑ 2. 不跑扫描/遍历类命令
      —— 禁止 Get-ChildItem -Recurse 扫盘、禁止全盘搜索
      —— 需要信息就直接问学员，或读取指定的具体路径

☑ 3. 每次只推进一小步，做完停下来等学员
      —— 不做"顺手把后面几步也做了"
      —— 不在一轮里连续执行多个未获批准的改动

☑ 4. 给"精确到行"的操作指令
      —— 说清楚改哪个文件、第几行、改成什么、为什么
      —— 学员执行后由 Agent 判断下一步
```

## 二、为什么有这四条（背景，不要遗忘）

```text
学员的目标是【学会】，不是【拿到结果】。

Agent 曾经犯过的错误（必须避免重演）：
  ① 诊断顺畅后就一路写代码、导数据、改配置、构建镜像
     → 学员失去了动手练习的机会（"你都做了，我学什么？"）
  ② 跑过 Get-ChildItem "D:\","E:\" -Recurse 全盘扫描
     → 这是不该做的操作，正确做法是问学员"文件在哪个目录"
  ③ 撤销时才发现项目没有 Git，只能靠"读过的原文 + 日志指纹"重建
     → 教训：改动前先留退路

核心原则：
   Agent 的效率 ≠ 学员的收获。
   学员亲手踩坑、亲手验证，才是学习发生的地方。
```

## 三、工作方式（默认流程）

```text
Agent 的职责：
  · 诊断问题（读代码、看日志、查证据）
  · 给出精确到行的改动方案 + 为什么这样改 + 期望输出 + 排查表
  · 学员执行后，判断结果、指出问题
  · 需要验证时，由 Agent 运行"只读"命令（查状态、看日志），不改文件

学员的职责：
  · 亲手改代码、亲手跑验证
  · 贴日志/截图给 Agent 判断

例外（Agent 可以直接动手的情况）：
  · 学员说"你改吧"
  · 学员明确要求创建某个文件（如本记忆文件）
  · 纯机械、零学习价值的操作（例如删除一批临时文件），且已获批准
```

## 四、教学风格要求

```text
· 中文讲解 + 英文技术术语（Promise / Event Loop / Cache-Aside / TTL 等不强行翻译）
· 讲解顺序：是什么 → 为什么需要 → 怎么用 → 代码 → 执行过程 → 实际作用 → 与其他知识的关系
· 代码不逐行翻译，优先解释：输入 → 执行顺序 → 关键逻辑 → 输出
· 复杂流程用 ASCII 或 Mermaid 图
· 一个知识点讲完 → 小结 → 确认理解 → 再进下一个
· 一次不要讲太多（沿用历史对话已形成的章节粒度）
· 不要因为看到技术名词就默认学员已掌握（"提到过 ≠ 讲解过 ≠ 理解了"）

已验证有效的教学手法（继续沿用）：
· 先让问题暴露，再讲解决方案（例：先看到脏数据，再讲缓存一致性）
· 每步给"期望输出"，让学员能自己判断对不对
· 出问题给"自查表"（现象 → 原因 → 处理）
· 结合真实 bug 讲原理（例：缓存"只写不读"的四层排查法）
```

## 五、环境事实（快速参考，避免重复排查）

```text
项目路径   : D:\blog-backend（Spring Boot 4.1.1 + Java 21）
学习笔记   : D:\Desktop\ai 全栈学习\

端口分配（都是被迫的，有原因）：
  应用     18080  ← 8080/8081 落在 Windows TCP 保留端口范围内，本机绑不上
  MySQL    3307   ← 3306 被 Docker 容器 customer-service-mysql 占用（phpStudy 的 MySQL 改到了 3307）
  Redis    6380   ← 6379 被 Docker 容器 customer-service-redis 占用（博客专用容器 blog-redis）

Redis 容器 : blog-redis（密码 blog123456，数据卷 blog-redis-data）
  连接命令 : docker exec -it blog-redis redis-cli -a blog123456 --no-auth-warning
MySQL 客户端: E:\phpstudy_pro\Extensions\MySQL8.0.12\bin\mysql.exe
  （PowerShell 里用 mysql 要用 --host= --port= 长格式，短参数 -h127.0.0.1 会被 PowerShell 吃掉）

Windows 保留端口查询 : netsh interface ipv4 show excludedportrange protocol=tcp
Maven 配置 : C:\Users\Shu\.m2\settings.xml（阿里云镜像；中央仓库超时会导致插件下载不全、
             进而 IDEA 整个 classpath 崩掉，表现为"所有依赖都红"）
```

## 六、常用验证命令（只读，Agent 可自行运行）

```powershell
# 应用/端口状态
Get-NetTCPConnection -LocalPort 18080 -State Listen

# 数据库被查了多少次（验证缓存是否真的挡住流量）
& "E:\phpstudy_pro\Extensions\MySQL8.0.12\bin\mysql.exe" --host=127.0.0.1 --port=3307 --user=root --password=root --execute="SHOW GLOBAL STATUS LIKE 'Com_select';"

# Redis 命令统计（验证缓存是"命中"还是"只写不读"）
docker exec blog-redis redis-cli -a blog123456 --no-auth-warning CONFIG RESETSTAT
docker exec blog-redis redis-cli -a blog123456 --no-auth-warning INFO commandstats

# 编译验证（Agent 在学员改完代码后运行）
& "C:\Users\Shu\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd" -o -B clean compile
```

## 七、当前学习进度（写于 2026-10-02）

```text
已完成：
  ✅ Vue3（博客项目九章实战 + 前后端联调）
  ✅ Java 基础（九站）
  ✅ Spring Boot：IOC/DI、AOP、统一响应、全局异常、MyBatis-Plus + MySQL、JWT 鉴权
  ✅ 第八站 事务管理（@Transactional，含自调用失效实验）
  ✅ 第九站 Redis 前半：独立容器 / Maven 环境修复 / 4.x 新序列化 API /
     Cache-Aside 缓存实现（实测命中 2ms，查库约 30ms 稳态）

当前卡点（已回退到这个状态）：
  · 缓存一致性未做：文章改了/删了，缓存不会失效 → 用户会看到旧数据
  · 没有写接口（POST/PUT/DELETE）
  · 防穿透（缓存空值）、防雪崩（TTL 随机抖动）未做
  · 全局异常的 HTTP 状态码是 200（应为 404/500）

原计划路线（不要擅自改动）：
  第九站 Redis 剩余部分 → 消息队列（了解级别）→ 回到"部署 + 简历 + 投递"主线

重要提醒：
  · 项目【尚未初始化 Git】—— 任何改动都不可回退
  · 学员已同意后续做 git init，但要求"等会再弄"
```

## 八、撤销/回退时的注意事项

```text
因为没有 Git，撤销只能靠人工重建。执行前必须：
  ① 先列出"要删除哪些文件、要还原哪些文件"，让学员核对
  ② 如实区分【精确还原】（手上有原文）和【近似重建】（跨多轮改动，无法逐字节还原）
  ③ 说明撤销后会损失哪些已验证可用的功能
  ④ 等学员确认再动手，不猜
  ⑤ 还原后必须做：编译验证 + 特征校验 + 停掉内存里的旧进程
```

---

*本文件由学员要求创建，用于约束 Agent 行为。修改需学员同意。*
