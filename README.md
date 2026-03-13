# RptZh08 报表执行工具

公积金报表 PROC_A_RPT_ZH08 执行工具，支持 TiDB/MySQL 数据库。

## 特性

- **FatJar 打包**：单个 jar 文件包含所有依赖，无需 lib 目录
- **默认配置**：从 jar 包内的 `db-config.properties` 读取默认配置
- **命令行覆盖**：命令行参数可覆盖默认配置
- **双模式执行**：支持报表执行模式和数据库连接测试模式

## 配置文件

默认配置文件路径：`src/main/resources/db-config.properties`

```properties
# 数据库连接URL (TiDB/MySQL)
db.url=jdbc:mysql://10.9.56.131:6000/ggetl?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true

# 数据库用户名
db.username=root

# 数据库密码
db.password=your_password

# JDBC驱动类名 (TiDB/MySQL)
db.driver-class-name=com.mysql.cj.jdbc.Driver
```

打包后配置文件被打入 jar 包内，作为默认配置使用。

## 编译打包

### 环境要求

- JDK 17 或更高版本
- Maven 3.8+

### 打包命令

```bash
cd /Users/sheldon/Documents/work_doc/国家公积金中心/demo
mvn clean package -DskipTests
```

打包完成后，生成文件：`target/rpt-zh08-service-1.0.0.jar`

这是一个 FatJar，包含所有依赖，可直接运行。

## 使用方法

### 命令行参数

```
java -jar rpt-zh08-service-1.0.0.jar [选项]
```

#### 执行模式

| 参数 | 说明 |
|------|------|
| `--mode=MODE` | 执行模式：`report`(执行报表,默认) 或 `test`(测试连接) |

#### 数据库连接参数（可选，覆盖默认配置）

| 参数 | 说明 |
|------|------|
| `--url=URL` | JDBC连接URL (完整URL) |
| `--host=HOST` | 数据库主机地址 |
| `--port=PORT` | 数据库端口 (默认: 3306) |
| `--database=DB` | 数据库名称 |
| `--user=USERNAME` | 数据库用户名 |
| `--password=PASSWORD` | 数据库密码 |
| `--driver=DRIVER` | JDBC驱动类 (默认: com.mysql.cj.jdbc.Driver) |

#### 报表参数

| 参数 | 说明 |
|------|------|
| `--date=YYYYMMDD` | 报表日期 (默认: 上个月最后一天)，仅当 mode=report 时有效 |

#### 帮助

| 参数 | 说明 |
|------|------|
| `--help`, `-h` | 显示帮助信息 |

### 使用示例

#### 1. 测试数据库连接

使用配置文件中的默认配置测试数据库连接：

```bash
java -jar rpt-zh08-service-1.0.0.jar --mode=test
```

#### 2. 执行报表（使用配置文件）

使用配置文件中的默认配置，只指定日期：

```bash
java -jar rpt-zh08-service-1.0.0.jar --date=20231031
```

或显式指定模式：

```bash
java -jar rpt-zh08-service-1.0.0.jar --mode=report --date=20231031
```

#### 3. 执行报表（覆盖数据库配置）

使用完整 JDBC URL 覆盖默认配置：

```bash
java -jar rpt-zh08-service-1.0.0.jar \
  --url=jdbc:mysql://192.168.1.100:3306/gjj_db?useSSL=false \
  --user=root \
  --password=123456 \
  --date=20231031
```

使用 host/port 覆盖默认配置：

```bash
java -jar rpt-zh08-service-1.0.0.jar \
  --host=192.168.1.100 \
  --port=3306 \
  --database=gjj_db \
  --user=root \
  --password=123456 \
  --date=20231031
```

#### 4. 使用默认日期执行报表

不指定日期，使用上个月最后一天：

```bash
java -jar rpt-zh08-service-1.0.0.jar
```

### Shell 脚本使用

提供了 `run-rpt-zh08.sh` 脚本（Linux/Mac），使用更简便。

#### 设置权限

```bash
chmod +x run-rpt-zh08.sh
```

#### 脚本参数

| 参数 | 说明 |
|------|------|
| `-m, --mode MODE` | 执行模式：report 或 test |
| `-h, --host HOST` | 数据库主机地址 |
| `-P, --port PORT` | 数据库端口 |
| `-d, --database DB` | 数据库名称 |
| `-u, --user USER` | 数据库用户名 |
| `-p, --password PWD` | 数据库密码 |
| `--url URL` | JDBC完整URL |
| `--driver DRIVER` | JDBC驱动类 |
| `-t, --date DATE` | 报表日期，格式YYYYMMDD |
| `--build` | 先执行maven打包再运行 |
| `--help` | 显示帮助信息 |

#### 脚本示例

**测试数据库连接：**

```bash
./run-rpt-zh08.sh --mode test
```

**使用配置文件执行报表：**

```bash
./run-rpt-zh08.sh -t 20231031
```

**覆盖默认配置执行报表：**

```bash
./run-rpt-zh08.sh \
  -h 192.168.1.100 \
  -P 3306 \
  -d gjj_db \
  -u root \
  -p 123456 \
  -t 20231031
```

**使用默认日期：**

```bash
./run-rpt-zh08.sh
```

**先打包再执行：**

```bash
./run-rpt-zh08.sh --build -t 20231031
```

### Windows 批处理使用

提供了 `run-rpt-zh08.bat` 脚本（Windows）。

使用方式与 Shell 脚本相同：

```bat
run-rpt-zh08.bat --mode test
run-rpt-zh08.bat -t 20231031
run-rpt-zh08.bat -h 192.168.1.100 -P 3306 -d gjj_db -u root -p 123456 -t 20231031
```

## JDBC URL 格式

### TiDB / MySQL

```
jdbc:mysql://主机:端口/数据库名?参数
```

**示例：**

```
jdbc:mysql://localhost:3306/gjj_db?useSSL=false&serverTimezone=Asia/Shanghai
```

**常用参数：**

| 参数 | 说明 |
|------|------|
| `useSSL=false` | 禁用SSL（开发环境） |
| `serverTimezone=Asia/Shanghai` | 设置时区 |
| `allowPublicKeyRetrieval=true` | 允许公钥检索 |

## 程序退出码

| 退出码 | 说明 |
|--------|------|
| 0 | 执行成功 |
| -1 | 执行失败（业务错误） |
| 其他 | 系统错误 |

## 常见问题

### 1. Java 命令未找到

**解决：** 确保已安装 JDK 并配置了 `JAVA_HOME` 环境变量

### 2. 数据库连接失败

**解决：**
- 检查数据库服务是否启动
- 检查网络连接是否正常
- 检查配置文件或命令行参数中的用户名密码是否正确
- 检查 JDBC URL 格式是否正确

### 3. 找不到 JAR 包

**解决：**
- 先执行 `mvn clean package` 打包
- 检查 `target` 目录是否存在 `rpt-zh08-service-1.0.0.jar`

### 4. 权限不足无法执行脚本

**解决：** 执行 `chmod +x run-rpt-zh08.sh` 添加执行权限

### 5. 如何修改默认配置

**解决：**
- 修改 `src/main/resources/db-config.properties` 文件
- 重新打包：`mvn clean package -DskipTests`

## 文件说明

| 文件 | 说明 |
|------|------|
| `src/main/java/com/example/CliApplication.java` | 命令行入口类 |
| `src/main/java/com/example/service/RptZh08Service.java` | 报表服务类 |
| `src/main/resources/db-config.properties` | 默认配置文件 |
| `pom.xml` | Maven 构建配置 |
| `run-rpt-zh08.sh` | Linux/Mac 执行脚本 |
| `run-rpt-zh08.bat` | Windows 执行脚本 |
| `README.md` | 本文档 |

## 技术支持

如有问题，请联系相关技术支持人员。
