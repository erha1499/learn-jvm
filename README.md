# learn-jvm

用“看得见”的方式学 Java 虚拟机（JVM）。

这是一份按单元组织的 JVM 学习手册，配有可以一步一步执行的交互演示：左边高亮的是这一步正在执行的代码，右边是这一刻栈帧、堆、常量池、Region 里发生的变化。字节码怎么在栈帧里执行、对象怎么在 Survivor 之间搬家、并发标记为什么会漏标，只读文字很难在脑子里还原，所以关键机制都尽量做成能动手的演示。

目标是理解原理，而不是背题。文中的结论尽量附上可运行的代码和真实输出：对象有多大、TLAB 快多少、四种收集器各停顿多久，都在 JDK 17 上实际测过。姊妹篇：[learn-juc](https://github.com/erha1499/learn-juc)（Java 并发）。

## 怎么读

| 入口 | 内容 |
| --- | --- |
| 交互学习手册（`docs/index.html`） | 完整正文 + 14 个嵌入式演示 + 可展开的面试回答 + 带答案的自测题 |
| 完整文字版（[`content/`](content/)） | 9 个单元和 1 个附录，每个单元一个文件，GitHub 上可直接阅读 |

网页是纯静态的，克隆仓库后直接用浏览器打开 `docs/index.html` 就能看（字体从 Google Fonts 加载，离线时退回系统字体）。在 GitHub 仓库设置里把 Pages 指向 `main` 分支的 `/docs` 目录，就能在线发布。

## 学习路线

| 单元 | 主题 | 一句话 | 演示 |
| --- | --- | --- | --- |
| 一 | 字节码与栈帧 | 每调用一个方法压一个栈帧，字节码在栈帧里搬数据、做计算 | 3 |
| 二 | 运行时数据区 | 私有的区域随线程生灭，共享的堆和方法区才是 GC 和 OOM 的主战场 | 2 |
| 三 | 类加载 | 第一次真正用到才初始化，“谁加载的”也是类身份的一部分 | 2 |
| 四 | 对象 | new 对象多数时候只是挪一下指针，对象 = 对象头 + 字段 + 填充 | 2 |
| 五 | 垃圾判定 | GC 不找垃圾，找活对象：从 GC Roots 走得到的都活着 | 1 |
| 六 | 回收算法与分代 | 没有完美的算法，分代就是给不同寿命的对象配不同的算法 | 2 |
| 七 | 垃圾收集器 | 收集器的演进就是把停顿越切越短 | 2 |
| 八 | 执行引擎 | 先解释执行，热点代码再编译成机器码 | — |
| 九 | 线上排查 | 排查靠证据链：指标、线程栈、GC 日志、堆转储 | — |
| 附录 | 高频面试题速查表 | 各单元「面试这样说」的一句话版 | — |

每个单元按同一个节奏组织：先看现象（能复现的代码和真实结果），再讲原理，然后动手操作演示，最后用“面试这样说”和自测题检验。

## 仓库结构

```text
content/     正文和数据，文字内容只改这里
  README.md      书名和导读，也是各单元的目录
  NN-*.md        各单元和附录，一个单元一个文件，按编号排序
  answers.json   自测题答案（键是题目原文）
  img/           文字版用到的配图（SVG，自带深色模式）
site/        网页源码
  build.py           构建脚本：把 content/ 的正文转成交互网页，并插入演示
  course.shell.html  交互手册的页面框架、样式和全部演示代码
scripts/     handbook.py 把各单元拼回整本（构建和飞书同步共用），sync_feishu.py 同步飞书
docs/        构建产物，用浏览器直接打开，或用 GitHub Pages 发布（不要手改）
examples/    文中所有可运行的示例代码，见 examples/README.md
```

## 修改与构建

只依赖 Python 3 标准库：

```bash
python3 site/build.py
```

改了 `content/` 或 `site/` 下的文件后运行一次，会重新生成 `docs/index.html` 并复制配图到 `docs/img/`。

新增一个演示，大致是三步：

1. 在 `site/course.shell.html` 里实现演示（大多数用现成的 `stepLab` 逐步引擎，只需写代码面板、步骤和右侧状态的渲染），并注册到 `LABS`。
2. 在 `site/build.py` 的 `RULES` 里登记演示插在正文的哪一段之后（按段落开头文字匹配）。
3. 运行构建，脚本会检查每个演示都插进了正文。

## 内容维护

正文只有一个来源：`content/` 下的 Markdown，改哪个单元就改对应的 `NN-*.md`。单元文件里单元名是一级标题、小节是二级标题；构建和同步时会拼回整本。其他地方都是从这里单向生成或同步的副本，不要在副本里直接修改。

源码细节、底层实现这类第一遍可以跳过的内容，写在 `### 深入一层（可跳过）：标题` 下面，范围到下一个标题为止。GitHub 上它是普通的小标题，网页版会渲染成默认收起的折叠块。

| 副本 | 怎么更新 |
| --- | --- |
| 网页（`docs/`） | 运行 `python3 site/build.py`，提交并推送 |
| 飞书文档（可选） | 运行 `FEISHU_DOC=<飞书文档链接或 token> python3 scripts/sync_feishu.py`，整篇覆盖。需要已登录的飞书命令行工具 `lark-cli`，配图转换依赖 macOS 自带的 `qlmanage` 和 `sips`；加 `--dry-run` 只生成转换结果，不上传 |

## 示例代码

`examples/` 里是手册引用的全部示例：`i = i++` 的字节码、栈深度、三种 OOM、类初始化时机、双亲委派与“同名不同类”、对象大小与字段布局、TLAB 对比、四种引用、GC 日志里的对象晋升、四种收集器的停顿对比、JIT 预热与逃逸分析，以及用来练习 jstat、jcmd、jstack 的内存泄漏和 CPU 飙高程序。运行方法见 [examples/README.md](examples/README.md)。

## 内容来源

手册以下列资料为线索重新组织和改写，并补充了原理解释、交互演示和实测代码：

- [小林coding · Java 虚拟机面试题](https://xiaolincoding.com/interview/jvm.html)
- [JavaGuide · Java 内存区域详解](https://javaguide.cn/java/jvm/memory-area.html)
- [JavaGuide · JVM 垃圾回收详解](https://javaguide.cn/java/jvm/jvm-garbage-collection.html)
- [JavaGuide · 类文件结构详解](https://javaguide.cn/java/jvm/class-file-structure.html)、[类加载过程详解](https://javaguide.cn/java/jvm/class-loading-process.html)、[类加载器详解](https://javaguide.cn/java/jvm/classloader.html)
- [JavaGuide · JDK 监控和故障处理工具总结](https://javaguide.cn/java/jvm/jdk-monitoring-and-troubleshooting-tools.html)、[Java 后端线上问题排查](https://javaguide.cn/java/jvm/jvm-in-action.html)
