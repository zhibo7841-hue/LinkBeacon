# Contributing

感谢你对 LinkBeacon 的关注。请在提交贡献前阅读项目文档，确保变更符合产品边界、隐私原则和当前架构方向。

## Contribution flow

1. Fork repository
2. Create branch
3. Commit changes
4. Open Pull Request

## Contribution requirements

- 保持代码质量和现有 Clean Architecture 分层。
- 为新增或修改的行为添加或更新测试。
- 不提交密钥、API Key、账号、私人路径或真实用户数据。
- 不引入网络数据上传、账号系统、广告或违反本地优先原则的功能。
- 不将 LinkBeacon 扩展为自动修复工具、确定性自动诊断系统或 SSH/Telnet 客户端。
- 变更应保持在已确认的产品范围内；新增范围请先通过项目决策记录确认。

## Before opening a pull request

请运行：

```text
./gradlew test
./gradlew assembleDebug
```

Pull Request 应说明变更目的、测试结果以及是否影响产品范围或隐私行为。
