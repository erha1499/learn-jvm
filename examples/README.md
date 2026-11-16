# 示例代码

手册里引用的所有示例，都在 JDK 17（64 位 HotSpot）上编译运行过。每个文件都是独立的类，没有包名，进入本目录后直接编译运行：

```bash
cd examples
javac -encoding UTF-8 StackDepth.java
java -Xint -Xss1m StackDepth
```

用到 `sun.misc.Unsafe` 的程序编译时会提示“内部专用 API”，可以忽略。运行参数写在每个文件开头的注释里，下表只列最关键的。

## 单元一 字节码与执行

| 文件 | 章节 | 看什么 |
| --- | --- | --- |
| `Calc.java` | 1.2、1.3 | `javap -c -v Calc`：字节码、版本号、常量池、`stack=2, locals=3` |
| `IPlusPlus.java` | 1.3 | `i = i++` 之后 i 还是 0；`javap -c IPlusPlus` 看 iload、iinc、istore 的顺序 |
| `StackDepth.java` | 1.4 | `java -Xint -Xss1m StackDepth`：栈大小、栈帧大小和递归深度的关系 |
| `JitWarmup.java` | 1.6 | 越跑越快；对比 `-Xint`；加 `-XX:+PrintCompilation` 看分层编译 |

## 单元二 运行时数据区

| 文件 | 章节 | 看什么 |
| --- | --- | --- |
| `MetaspaceOOM.java` | 2.3 | `java -XX:MaxMetaspaceSize=16m MetaspaceOOM`：元空间溢出 |
| `StringPool.java` | 2.4 | 八个 `==` 的结果，intern 登记的是哪个对象 |
| `DirectOOM.java` | 2.5 | `java -Xmx1g -XX:MaxDirectMemorySize=10m DirectOOM`：直接内存溢出 |
| `HeapOOM.java` | 2.6 | `java -Xmx20m -XX:+HeapDumpOnOutOfMemoryError HeapOOM`：堆溢出并自动转储 |

## 单元三 类加载

| 文件 | 章节 | 看什么 |
| --- | --- | --- |
| `InitTiming.java` | 3.1 | 哪些操作会、哪些不会触发类的初始化 |
| `PreparePuzzle.java` | 3.2 | 准备阶段清零、初始化阶段按顺序赋值：输出 `a = 1, b = 0` |
| `LoadVsInit.java` | 3.3 | `loadClass` 只加载，`Class.forName` 会初始化 |
| `ClinitOnce.java` | 3.4 | 两个线程同时触发初始化，静态代码块只执行一次。`java -Dms=4000 ClinitOnce` 后用 jstack 看等待的线程 |
| `LoaderTree.java` | 3.5 | 三层类加载器和它们的父子链 |
| `TwoLoaders.java` | 3.6 | 自定义类加载器，同一个类被两个加载器加载成“同名不同类”。先运行 `javac -encoding UTF-8 -d plugin plugin/Greeter.java` |

## 单元四 对象

| 文件 | 章节 | 看什么 |
| --- | --- | --- |
| `NewObject.java` | 4.1 | `javap -c NewObject`：`new`、`dup`、`invokespecial <init>` |
| `TlabBench.java` | 4.2 | 对比 `java TlabBench` 和 `java -XX:-UseTLAB TlabBench` 的耗时 |
| `FieldLayout.java` | 4.3 | 字段的偏移量和重排；加 `-XX:-UseCompressedClassPointers -XX:-UseCompressedOops` 再看一次 |
| `agent/` | 4.3 | 用 `Instrumentation.getObjectSize` 量对象大小，运行方法见下 |
| `EscapeDemo.java` | 4.5 | `java -Xmx64m EscapeDemo` 和加上 `-XX:-DoEscapeAnalysis` 时的 GC 次数 |

`agent/` 需要先打成 Java Agent：

```bash
cd examples/agent
javac SizeAgent.java ObjectSize.java
printf "Premain-Class: SizeAgent\n" > manifest.txt
jar cfm agent.jar manifest.txt SizeAgent.class
java -javaagent:agent.jar ObjectSize
```

## 单元五 垃圾判定

| 文件 | 章节 | 看什么 |
| --- | --- | --- |
| `CycleRef.java` | 5.1 | `java -Xlog:gc CycleRef`：互相引用的两个对象照样被回收 |
| `RefTypes.java` | 5.3 | `java -Xmx64m RefTypes`：强、软、弱、虚引用在 GC 和内存吃紧时的区别 |

## 单元六 回收算法与分代

| 文件 | 章节 | 看什么 |
| --- | --- | --- |
| `MinorGC.java` | 6.3 | `java -XX:+UseSerialGC -Xms24m -Xmx24m -Xmn10m -Xlog:gc,gc+heap=info MinorGC`：Survivor 放不下，对象直接进老年代 |
| `Tenuring.java` | 6.3 | `java -XX:+UseSerialGC -Xms40m -Xmx40m -Xmn20m -XX:MaxTenuringThreshold=3 -Xlog:gc+age=trace,gc+heap=info Tenuring`：年龄一次次增长，到阈值后晋升；把 `-Xmn20m` 换成 `-Xmn10m` 能看到动态年龄判断 |

## 单元七 垃圾收集器

| 文件 | 章节 | 看什么 |
| --- | --- | --- |
| `GcCompare.java` | 7.7 | 同一负载分别加 `-XX:+UseSerialGC`、`-XX:+UseParallelGC`、`-XX:+UseG1GC`、`-XX:+UseZGC`（都配 `-Xms2g -Xmx2g -Xlog:gc`），对比停顿。每次运行 10 秒 |

## 单元八 线上排查

这两个程序会一直运行，用来练习工具，看完用 Ctrl+C 结束。

| 文件 | 章节 | 看什么 |
| --- | --- | --- |
| `LeakDemo.java` | 8.3、8.5 | `java -Xmx256m -XX:NativeMemoryTracking=summary LeakDemo`，再用 `jps`、`jstat -gcutil <pid> 1000`、`jcmd <pid> GC.class_histogram`、`jcmd <pid> VM.native_memory summary` 观察。约两分钟后 OOM |
| `CpuHog.java` | 8.4 | 隔几秒打两次 `jstack <pid>`，比较各线程的 `cpu=`，找出占满 CPU 的线程和代码行 |
