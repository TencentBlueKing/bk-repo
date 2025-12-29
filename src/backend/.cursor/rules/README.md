# BK-REPO 后端开发规则

本目录包含 BK-REPO 后端项目的 Cursor AI 开发规则，使用 `.mdc` 格式（带元数据的 Markdown）。

## 📁 规则文件

| 文件 | 适用范围 | 说明 |
|-----|---------|------|
| `backend.mdc` | 所有 `.kt` 文件 | 后端通用规范（总是应用） |
| `controller.mdc` | Controller 类 | REST API 接口开发规范 |
| `service.mdc` | Service 类 | 业务逻辑实现规范 |
| `repository.mdc` | DAO/Repository 类 | MongoDB 数据访问规范 |
| `model.mdc` | Model/POJO 类 | 数据模型定义规范 |
| `exception.mdc` | 异常处理类 | 异常定义和处理规范 |
| `test.mdc` | 测试文件 | 测试开发规范 |
| `gradle.mdc` | Gradle 配置 | 构建配置规范 |

## 🎯 使用方式

Cursor AI 会根据文件路径自动应用相应的规则：

- 编辑任何 `.kt` 文件 → **总是应用** `backend.mdc` 规则（文件头、命名、日志等）
- 编辑 `*Controller.kt` → 额外应用 `controller.mdc` 规则
- 编辑 `*Service*.kt` → 额外应用 `service.mdc` 规则
- 编辑 `*Dao.kt` → 额外应用 `repository.mdc` 规则
- 编辑 `model/*.kt` → 额外应用 `model.mdc` 规则
- 编辑 `*Test.kt` → 额外应用 `test.mdc` 规则
- 编辑 `*.gradle.kts` → 额外应用 `gradle.mdc` 规则

## 📝 规则格式

每个 `.mdc` 文件格式：

```
---
description: 规则描述
globs: 适用的文件模式
alwaysApply: 是否总是应用
---

规则内容（Markdown 格式）
```

## 🏗️ 规则架构

```
backend.mdc (总是应用 - 282 行)
    ├─ 项目架构与技术栈 (42 行)
    │   ├─ Spring Boot 3.x + Spring Cloud
    │   ├─ Kotlin + Gradle (Kotlin DSL)
    │   ├─ MongoDB + Redis + Pulsar
    │   └─ 微服务模块结构 (api/biz/boot)
    │
    ├─ 编码规范 (32 行)
    │   ├─ 包命名和类命名规范
    │   ├─ 依赖注入（构造函数注入）
    │   └─ 日志规范（格式、级别、业务标识）
    │
    ├─ Kotlin 编码规范 (120 行) ⭐
    │   ├─ 变量声明（val vs var）
    │   ├─ 数据类（data class）
    │   ├─ 空安全（?.、?:、禁止!!）
    │   ├─ 惯用语法（when、单表达式函数、默认参数）
    │   ├─ 扩展函数（代替工具类）
    │   ├─ 作用域函数（let、run、apply、also、with）
    │   ├─ 集合操作（函数式编程）
    │   ├─ 字符串处理（模板、三引号）
    │   ├─ 设计模式（sealed class、enum、object）
    │   ├─ 资源管理（use 函数）
    │   ├─ 协程（suspend、作用域）
    │   └─ 可见性控制（private、internal、final）
    │
    ├─ 代码质量规范 (42 行)
    │   ├─ 命名规范（PascalCase/camelCase/UPPER_SNAKE_CASE）
    │   ├─ 注释规范（KDoc、避免无意义注释）
    │   ├─ 代码组织（函数长度、缩进、简洁性）
    │   ├─ 异常处理（ErrorCodeException、Result 包装器）
    │   ├─ 性能优化（避免 N+1、流式处理、缓存）
    │   ├─ 事务管理（@Transactional、避免耗时操作）
    │   └─ 依赖注入（构造函数、可测试性）
    │
    └─ 安全规范 (16 行)
        ├─ 输入验证（Bean Validation、防注入）
        ├─ 权限控制（@Principal、@Permission）
        └─ 敏感数据（脱敏、加密）
            ↓
根据文件类型额外应用：
    ├─ controller.mdc  (API 注解、权限、审计)
    ├─ service.mdc     (业务流程、异常、转换)
    ├─ repository.mdc  (查询、更新、性能)
    ├─ model.mdc       (实体、DTO、索引)
    ├─ exception.mdc   (异常定义、处理)
    ├─ test.mdc        (测试结构、Mock、断言)
    └─ gradle.mdc      (依赖管理、模块配置)
```

## 🔧 维护

更新规则时请：
1. **公共规则** → 放到 `backend.mdc`（会应用到所有 Kotlin 文件）
2. **特定规则** → 放到对应的具体文件（只在匹配时应用）
3. 保持规则精简，只包含核心要点
4. 更新 `globs` 确保正确匹配文件

---

**提示**：这些规则会被 Cursor AI 自动读取，无需手动引用。所有 Kotlin 文件都会自动应用 `backend.mdc` 规则。

