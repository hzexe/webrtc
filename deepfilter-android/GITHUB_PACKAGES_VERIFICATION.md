# GitHub Packages 部署验证清单

本文档提供了验证 GitHub Packages 部署配置的详细步骤，确保 Maven 包能够成功发布到 GitHub Packages。

## 1. 仓库可见性验证

### 检查仓库可见性

1. 访问仓库页面：https://github.com/hzexe/webrtc
2. 点击仓库页面的 "Settings" 标签
3. 在 "Danger Zone" 部分查看 "Change repository visibility"
4. 确认仓库状态：
   - **公开仓库（Public）**：任何人都可以访问和下载包
   - **私有仓库（Private）**：只有授权用户可以访问和下载包

### 修改仓库可见性（如果需要）

如果仓库是私有的，但需要公开访问包：

1. 进入仓库 Settings
2. 滚动到 "Danger Zone"
3. 点击 "Change repository visibility"
4. 选择 "Make public"
5. 确认更改

**注意**：将私有仓库改为公开仓库是永久性操作，请谨慎操作。

## 2. GitHub Packages 功能启用验证

### 检查 Packages 功能

1. 访问仓库页面
2. 点击顶部的 "Packages" 标签
3. 如果看到 "No packages published yet" 或已发布的包列表，说明 Packages 功能已启用

### 手动启用 Packages 功能（如果需要）

如果 Packages 标签不存在或无法访问：

1. 进入仓库 Settings
2. 在左侧菜单中找到 "Packages" 部分
3. 确保 "Packages" 功能已启用
4. 如果看到 "Enable Packages" 按钮，点击启用

## 3. GitHub Actions 权限验证

### 检查 Workflow 权限

1. 进入仓库 Settings
2. 点击 "Actions" -> "General"
3. 在 "Workflow permissions" 部分，确保选择：
   - ✅ "Read and write permissions"
   - ✅ "Allow GitHub Actions to create and approve pull requests"

### 修改 Workflow 权限（如果需要）

如果权限设置不正确：

1. 进入仓库 Settings -> Actions -> General
2. 在 "Workflow permissions" 部分
3. 选择 "Read and write permissions"
4. 保存更改

## 4. Personal Access Token (PAT) 验证

### 创建或更新 PAT

1. 访问：https://github.com/settings/tokens
2. 点击 "Generate new token" -> "Generate new token (classic)"
3. 设置 Token 名称（如：GitHub Packages Deployment）
4. 设置过期时间（建议：90天或更短）
5. 选择权限（Scopes）：
   - ✅ `repo`（完整仓库访问权限）
   - ✅ `write:packages`（写入包权限）
   - ✅ `read:packages`（读取包权限）
   - ✅ `delete:packages`（删除包权限，可选）
6. 点击 "Generate token"
7. **重要**：立即复制并保存 token，因为只会显示一次

### 验证 PAT 权限

确保生成的 PAT 具有以下权限：
- `repo` - 完整仓库访问权限
- `write:packages` - 写入包权限
- `read:packages` - 读取包权限

## 5. Maven 配置验证

### 本地 Maven Settings.xml

在 `~/.m2/settings.xml` 中配置：

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_GITHUB_TOKEN</password>
        </server>
    </servers>
</settings>
```

### 验证配置

1. 确保 `settings.xml` 文件存在
2. 验证用户名和密码是否正确
3. 确认 PAT 具有正确的权限

## 6. POM 文件配置验证

### 检查 Distribution Management

在 `pom.xml` 中确认：

```xml
<distributionManagement>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/hzexe/webrtc</url>
    </repository>
</distributionManagement>
```

### 验证仓库 URL

确保 URL 格式正确：
- 格式：`https://maven.pkg.github.com/{owner}/{repo}`
- 示例：`https://maven.pkg.github.com/hzexe/webrtc`

## 7. GitHub Actions Workflow 验证

### 检查 Workflow 配置

在 `.github/workflows/build-deepfilter-android.yml` 中确认：

```yaml
jobs:
  build-deepfilter:
    permissions:
      contents: read
      packages: write
```

### 验证环境变量

确保 Workflow 中正确设置：
- `MAVEN_USERNAME`: `${{ github.actor }}`
- `MAVEN_PASSWORD`: `${{ secrets.GITHUB_TOKEN }}`

## 8. 测试部署流程

### 手动触发 Workflow

1. 访问仓库的 "Actions" 标签
2. 选择 "Publish DeepFilterNet Android Library" workflow
3. 点击 "Run workflow"
4. 选择分支（如：main）
5. 点击 "Run workflow" 按钮

### 监控部署过程

1. 查看 Workflow 运行日志
2. 检查每个步骤是否成功
3. 特别关注 "Maven 打包和部署" 步骤

### 验证发布结果

1. 访问仓库的 "Packages" 标签
2. 查看是否新发布了包
3. 点击包查看详细信息
4. 验证版本号和文件列表

## 9. 常见问题排查

### 问题 1：401 Unauthorized

**原因**：认证失败

**解决方案**：
1. 验证 PAT 是否正确
2. 确认 PAT 具有正确的权限
3. 检查 `settings.xml` 配置是否正确
4. 确保 PAT 未过期

### 问题 2：404 Not Found

**原因**：仓库或包不存在

**解决方案**：
1. 确认仓库 URL 正确
2. 确认仓库可见性设置
3. 确保 Packages 功能已启用
4. 检查仓库名称是否正确

### 问题 3：403 Forbidden

**原因**：权限不足

**解决方案**：
1. 检查 Workflow 权限设置
2. 确认 PAT 具有 `write:packages` 权限
3. 验证仓库访问权限

### 问题 4：Maven 部署失败

**原因**：Maven 配置问题

**解决方案**：
1. 检查 POM 文件配置
2. 验证 Maven 插件版本
3. 确认 Java 版本兼容性
4. 查看详细错误日志

## 10. 成功验证清单

完成以下所有项目后，即可确认配置正确：

- [ ] 仓库可见性已设置（公开或私有）
- [ ] GitHub Packages 功能已启用
- [ ] GitHub Actions 权限已配置为 "Read and write permissions"
- [ ] Personal Access Token 已创建并具有正确权限
- [ ] Maven `settings.xml` 已正确配置
- [ ] POM 文件中的 `distributionManagement` 配置正确
- [ ] GitHub Actions Workflow 权限已正确设置
- [ ] Workflow 能够成功运行
- [ ] 包能够成功发布到 GitHub Packages
- [ ] 可以在仓库的 Packages 标签中看到发布的包

## 11. 后续维护

### 定期更新 PAT

- PAT 建议设置过期时间（90天或更短）
- 过期前创建新的 PAT 并更新配置
- 删除不再使用的 PAT

### 监控包使用情况

1. 定期查看 GitHub Packages 使用统计
2. 监控下载次数和版本使用情况
3. 根据需要更新包版本

### 清理旧版本

1. 定期清理不再使用的包版本
2. 保留必要的版本以支持旧客户端
3. 使用 GitHub API 或手动删除旧版本

## 12. 参考文档

- [GitHub Packages 官方文档](https://docs.github.com/en/packages)
- [使用 Maven 发布 Java 包到 GitHub Packages](https://docs.github.com/en/actions/tutorials/publish-packages/publish-java-packages-with-maven)
- [GitHub Actions 权限文档](https://docs.github.com/en/actions/security-guides/automatic-token-authentication)
- [Personal Access Token 文档](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token)

## 联系支持

如果遇到无法解决的问题：

1. 查看 GitHub 社区论坛
2. 提交 GitHub Support 请求
3. 查阅项目 Issues 页面

---

**最后更新**：2026-01-15
**维护者**：WebRTC Team
