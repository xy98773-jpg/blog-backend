# blog-backend

一个博客系统的后端，用 Spring Boot 4 写的。前端是 Vue3，仓库在别处。

做它的初衷很简单：我之前主要在用 Python 做 Agent 应用，后端这块一直是短板——能调通 API，但说不清楚工程上该怎么做。所以拿博客这个最熟的业务场景，从头到尾把一套后端该有的东西自己写了一遍：分层、鉴权、缓存、异常处理，写的过程中踩了不少坑，也都记在笔记里了。

## 技术栈

- Java 21 + Spring Boot 4.1.1
- MyBatis-Plus 3.5.17（Spring Boot 4 有专门的 starter 版本）
- MySQL 8.0
- Redis 7（缓存）
- Spring Security 7 + jjwt 0.12.6（无状态 JWT 鉴权）

## 实现了什么

**接口分层**：Controller 只接请求返响应，业务逻辑在 Service，数据库操作在 Mapper。统一用 `ApiResponse<T>` 包装返回结果，`code` / `message` / `data` 三个字段。

**JWT 鉴权**：登录后签发 Token，之后的请求靠 `JwtAuthenticationFilter` 解析。服务端不存 Session，重启后已签发的 Token 依然有效。

权限按"读接口匿名、写接口必须认证"来分：

| 接口 | 是否需要 Token |
|---|---|
| `GET /articles`、`GET /articles/{id}` | 不需要 |
| `POST /articles`、`PUT /articles/{id}`、`DELETE /articles/{id}` | 需要 |
| `POST /auth/register`、`POST /auth/login` | 不需要 |

**Redis 缓存**：列表和详情接口都做了 Cache-Aside 缓存，命中时 2ms 左右，查库 30ms 左右。

缓存这块花了最多时间，因为碰上一个很隐蔽的问题：接口一切正常，日志也正常，但缓存其实从来没被读到过——每次请求都在查库，缓存只是被反复覆盖。最后是靠数数据库的 `Com_select` 次数和 Redis 的 `commandstats` 才发现的，根因是缓存对象缺少无参构造，Jackson 反序列化时直接抛异常，被我的 try-catch 降级逻辑吞掉了。具体过程记在笔记里。

顺带处理了几个缓存常见问题：

- **一致性**：写操作后删缓存（不是更新缓存），顺序是先更新数据库再删缓存
- **穿透**：查不到的 id 也缓存一个空值，TTL 60 秒，避免被反复打库
- **雪崩**：TTL 加 0~60 秒随机抖动，避免一批 key 同时过期
- **降级**：Redis 挂了就当没有缓存，直接查库，不影响接口可用性

**全局异常处理**：`@RestControllerAdvice` 统一接异常，配合 `@ResponseStatus` 返回真实的 HTTP 状态码——不存在的文章是 404，服务器错误是 500，而不是一律 200 然后把错误码塞在响应体里。

**事务**：注册用户时会同时写 `user` 和 `user_log` 两张表，用了 `@Transactional` 保证要么都成功要么都回滚。

**AOP 日志**：`@Around` 切面记录每个 Controller 方法的调用和耗时，调试时挺好用。

## 接口

| 方法 | 路径 | 说明 | 认证 |
|---|---|---|---|
| POST | `/auth/register` | 注册 | 否 |
| POST | `/auth/login` | 登录，返回 Token | 否 |
| GET | `/articles` | 文章列表（带缓存） | 否 |
| GET | `/articles/{id}` | 文章详情（带缓存） | 否 |
| POST | `/articles` | 新增文章 | 是 |
| PUT | `/articles/{id}` | 修改文章 | 是 |
| DELETE | `/articles/{id}` | 删除文章 | 是 |

写接口需要在请求头带 Token：

```
Authorization: Bearer <登录返回的 token>
```

响应格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

## 跑起来

### 需要准备

- JDK 21
- Maven（或者直接用项目里的 `mvnw`）
- MySQL 8（我本地用的是 phpStudy 里的）
- Redis 7（我用 Docker 跑的）

### 数据库

```sql
CREATE DATABASE blog_db DEFAULT CHARACTER SET utf8mb4;

USE blog_db;

CREATE TABLE article (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    summary VARCHAR(500),
    content TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL
);

CREATE TABLE user_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 配置

`src/main/resources/application.properties` 里的值都写成了 `${环境变量:默认值}` 的形式，所以本地不用设任何环境变量也能跑，默认值是按我本机环境填的：

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3307/blog_db?...}
spring.data.redis.port=${SPRING_DATA_REDIS_PORT:6380}
server.port=${SERVER_PORT:18080}
```

你本机如果端口不一样，改默认值或者设环境变量都行：

```bash
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/blog_db?...
SPRING_DATA_REDIS_PORT=6379
SERVER_PORT=8080
JWT_SECRET=换成你自己的随机字符串，至少32字节
```

> 端口之所以是 18080 / 3307 / 6380 这几个不常见的值：8080 和 8081 在我机器上落在 Windows 的 TCP 保留端口范围里（`netsh interface ipv4 show excludedportrange protocol=tcp` 能看到），绑不上；3306 和 6379 分别被机器上另一个项目的 Docker 容器占着。

### 启动

```bash
./mvnw spring-boot:run
```

或者用 IDEA 直接跑 `BlogBackendApplication`。

### 试一下

```bash
# 注册
curl -X POST http://localhost:18080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# 登录拿 Token
curl -X POST http://localhost:18080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# 查列表（不用 Token）
curl http://localhost:18080/articles

# 新增（要 Token）
curl -X POST http://localhost:18080/articles \
  -H "Authorization: Bearer <上面拿到的 token>" \
  -H "Content-Type: application/json" \
  -d '{"title":"标题","summary":"摘要","content":"正文"}'
```

## 目录结构

```
src/main/java/com/xujinzhou/blogbackend/
├── controller/      接口层
├── service/         业务逻辑（缓存逻辑也在这里）
├── mapper/          数据库访问（MyBatis-Plus 的 BaseMapper）
├── entity/          数据库实体
├── dto/             缓存专用的包装对象
├── config/          Redis 序列化配置
├── security/        JWT 工具、过滤器、权限规则
├── handler/         全局异常处理
├── aspect/          AOP 日志切面
├── exception/       自定义异常
└── common/          统一响应封装
```

`dto` 下面那两个类可能有同学会觉得多余，解释一下：`CachedArticleList` 和 `CachedArticle` 是专门为缓存建的包装对象。因为 Java 的泛型在运行时会被擦除，`List<Article>` 存进 Redis 再读出来会变成 `List<LinkedHashMap>`；而且 Redis 存不下裸 `null`，没法表达"这个 id 确定不存在"这个信息。包一层具体类，这两个问题就都解决了。

## 还没做的

- 分页和条件查询（现在列表是全量返回）
- 缓存击穿的互斥锁（当前流量不值得做，方案清楚）
- 接口文档（SpringDoc / Swagger）
- 单元测试（现在只有一个 Spring Boot 默认生成的空测试）
- Docker 部署

## 一点说明

这是我学习后端过程中写的项目，代码结构和注释偏向"给自己看"的风格，注释写得比较啰嗦，主要是为了以后回看时能想起来当时为什么这么写。如果哪里写得不对或者有更好的做法，欢迎提 issue。
