# 单元九 线上排查：工具、OOM、CPU 飙高与调优

本单元的核心：排查靠**证据链**：先看指标确认现象，再取证（线程栈、GC 日志、堆转储），最后用数据验证修复。工具只是取证的手段，调参数是最后一步。

**学完这个单元，你能：**

- 用 jps、jstat、jcmd、jstack 观察一个运行中的 Java 进程，读懂一行 GC 日志。
- 按步骤排查内存泄漏和 CPU 飙高，从进程一路定位到具体的代码行。
- 说出“堆没满、进程内存却很高”的原因，以及调优的基本思路和常用参数。

前八个单元讲的是原理，本单元把它们用起来。顺序是：先认识工具箱（9.1），再学会读 GC 日志（9.2），然后用两个模拟的故障程序走一遍内存泄漏（9.3）和 CPU 飙高（9.4）的排查，接着看频繁 GC 和堆外内存（9.5），最后总结调优的思路（9.6）。

## 9.1 工具箱

JDK 自带的命令行工具就能解决大部分问题，都在 JDK 的 `bin` 目录下：

| 工具 | 用途 | 常用写法 |
| --- | --- | --- |
| jps | 列出本机的 Java 进程和进程号 | `jps -l` |
| jstat | 按固定间隔打印 GC 统计 | `jstat -gcutil <pid> 1000` |
| jcmd | 万能工具，下面几样都能做 | `jcmd <pid> help` 列出所有命令 |
| jstack | 打印线程栈（线程转储） | `jstack <pid>`，等价于 `jcmd <pid> Thread.print` |
| jmap | 对象统计、堆转储 | `jmap -histo:live <pid>`、`jmap -dump:live,format=b,file=heap.hprof <pid>` |

jcmd 最常用的几个命令：`GC.heap_info`（堆的概况）、`GC.class_histogram`（每个类有多少实例、占多少内存）、`GC.heap_dump <文件>`（堆转储）、`VM.flags`（实际生效的参数）、`VM.native_memory summary`（本地内存统计，需要启动时加 `-XX:NativeMemoryTracking=summary`）。

图形化工具：JConsole 和 VisualVM 看实时曲线；**MAT**（Eclipse Memory Analyzer）分析堆转储文件；JDK Flight Recorder（JFR）开销很低，可以在生产环境持续录制，用 JDK Mission Control 查看；阿里开源的 **Arthas** 可以直接 attach 到线上进程，查看最忙的线程、反编译、追踪方法耗时。

拿 `examples/LeakDemo.java` 试一试。它模拟一个只进不出的缓存：每个“请求”往静态 HashMap 里放一条 1KB 的记录，从来不删。用 `-Xmx256m` 启动后（JDK 17 实测）：

```text
$ jps -l
19223 LeakDemo

$ jcmd 19223 GC.heap_info
 garbage-first heap   total 262144K, used 41952K [0x00000007f0000000, 0x0000000800000000)
  region size 1024K, 4 young (4096K), 3 survivors (3072K)
 Metaspace       used 186K, committed 384K, reserved 1114112K
```

`jstat -gcutil <pid> <间隔毫秒>` 每一行是一次采样，各列含义：

| 列 | 含义 |
| --- | --- |
| S0、S1、E、O、M、CCS | Survivor 0、Survivor 1、Eden、老年代、元空间、压缩类空间的使用率（%） |
| YGC、YGCT | Young GC 的次数和总耗时（秒） |
| FGC、FGCT | Full GC 的次数和总耗时 |
| CGC、CGCT | 并发 GC 阶段（如 G1 的 Remark、Cleanup）的次数和耗时 |
| GCT | 所有 GC 的总耗时 |

## 9.2 读懂 GC 日志

GC 日志是判断“是不是 GC 的问题”最直接的证据，生产环境应该一直开着：

- JDK 9 及以后：`-Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=5,filesize=20m`（按大小滚动，保留 5 个文件）。
- JDK 8：`-XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:gc.log`。

拿单元七 G1 的一行日志来读：

```text
[0.050s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 110M->111M(2048M) 16.331ms
```

| 片段 | 含义 |
| --- | --- |
| `[0.050s]` | 进程启动后 0.050 秒 |
| `GC(0)` | 第 0 次 GC，同一次 GC 的多行日志编号相同 |
| `Pause Young (Normal)` | 停顿类型：普通的 Young GC |
| `(G1 Evacuation Pause)` | 触发原因：Eden 满了，要把活对象疏散出去 |
| `110M->111M(2048M)` | GC 前堆用了 110M，GC 后 111M，堆总共 2048M |
| `16.331ms` | 这次停顿的时长 |

这次回收后堆反而多了 1M：程序刚启动，所有对象都还活着，而 GC 期间分配的新对象也算了进去。看 GC 日志时主要看四件事：**多久一次、每次停多久、回收后堆的占用是否越来越高、Full GC 是什么原因触发的**。“回收后的占用”一路上涨，就是内存泄漏的信号。

## 9.3 内存泄漏与 OOM

**排查步骤**：

1. **确认是哪块内存**：先看 OOM 的提示信息（2.6 节的速查表）。
2. **看趋势**：用 jstat 或 GC 日志观察老年代在 GC 之后的占用。正常应用是锯齿形，回收后回到差不多的水位；泄漏是水位一路上涨。
3. **取证**：隔一段时间做两次 `GC.class_histogram` 对比，看哪个类的实例数一直在涨；或者做一次堆转储。最好用 `-XX:+HeapDumpOnOutOfMemoryError` 让 JVM 在出事那一刻自动转储。
4. **分析**：用 MAT 打开堆转储。**Histogram** 看哪个类占得多；**Dominator Tree** 看哪个对象“支配”了最多的内存（它被回收，这些内存就能释放）；对可疑对象用 **Path to GC Roots** 查是谁引用着它不放。
5. **修复并验证**：改完后用同样的方式观察，确认老年代水位不再上涨。

LeakDemo 用 `jstat -gcutil -t <pid> 15000` 每 15 秒采样一次，JDK 17 实测（`Timestamp` 是进程启动后的秒数）：

```text
Timestamp         S0     S1     E      O      M     CCS    YGC     YGCT     FGC    FGCT     CGC    CGCT       GCT
           23.7   0.00   0.00  46.15  34.02  48.64   4.97      2     0.012     1     0.004     0     0.000     0.016
           38.7   0.00 100.00  41.67  61.01  48.74   4.97      4     0.029     1     0.004     2     0.000     0.033
           53.7   0.00 100.00  33.33  70.78  48.74   4.97      8     0.054     1     0.004     4     0.001     0.059
           68.8   0.00 100.00  56.25  70.35  48.74   4.97     11     0.072     1     0.004     6     0.001     0.077
           83.7   0.00 100.00  33.33  74.52  48.74   4.97     14     0.092     1     0.004     8     0.001     0.097
           98.7   0.00 100.00  18.18  87.69  47.30   3.78     17     0.116     1     0.004    10     0.002     0.122
          113.7   0.00 100.00   0.00  99.90  47.30   3.78     25     0.142     1     0.004    16     0.003     0.150
          121.2   0.00   0.00   0.00  99.72  47.40   3.78     40     0.158    16     0.448    22     0.004     0.610
```

- 老年代 O 从 34% 一路涨到 99.9%，中间的 GC 怎么回收都降不下来，这是泄漏的典型曲线。
- 最后几秒 FGC 从 1 次猛增到 16 次：堆满了，JVM 一次接一次地 Full GC，却几乎回收不到东西。紧接着进程抛出 `OutOfMemoryError`。

取证时做一次类直方图，嫌疑立刻就清楚了（运行十几秒时，前三名）：

```text
 num     #instances         #bytes  class name (module)
-------------------------------------------------------
   1:         75471       36591080  [B (java.base@17.0.16)
   2:         34798        1113536  java.util.HashMap$Node (java.base@17.0.16)
   3:         41586         998064  java.lang.String (java.base@17.0.16)
```

`[B` 是 byte 数组。大量 byte 数组、HashMap 节点和 String，三者数量相近，指向“某个 HashMap 不停地放入 String 作 key、byte 数组作 value”。到 MAT 里对这些 byte 数组查 Path to GC Roots，就能找到 `LeakDemo.CACHE` 这个静态字段。

**常见的泄漏来源**：当缓存用的静态集合没有淘汰策略；线程池里的 ThreadLocal 用完没有 remove（learn-juc 单元九）；注册了监听器、回调却从不注销；连接、流没有关闭；自定义类加载器被引用着导致类卸载不掉（元空间泄漏）。

**生产环境做堆转储要注意**：转储期间进程会停顿，堆越大停得越久；文件和堆的使用量差不多大，先确认磁盘空间；堆里可能有用户数据，文件要按敏感数据管理。条件允许的话，先把这台机器从负载均衡里摘下来再转储。

## 9.4 CPU 飙高

**Linux 上的经典四步**：

1. `top` 找到 CPU 最高的 Java 进程，记下进程号 pid。
2. `top -Hp <pid>` 列出这个进程里的线程，找到 CPU 最高的线程号 tid。
3. `printf '%x\n' <tid>` 把线程号转成十六进制。
4. `jstack <pid> | grep -A 20 'nid=0x<十六进制>'`：jstack 里每个线程的 `nid` 就是操作系统线程号，找到它的栈，就看到它在执行哪段代码。

JDK 11 起，jstack 还会在每个线程后面打印它累计用了多少 CPU 时间（`cpu=`），所以不依赖 `top -H` 也能定位：隔几秒打两次 jstack，**`cpu=` 涨得最快的线程就是元凶**。这个办法在 macOS、Windows 上同样适用。

`examples/CpuHog.java` 启动了三个睡觉的线程和一个死循环线程 `order-calc`。`ps` 显示进程 CPU 占用 99.2%。启动 3 秒和 8 秒时各打一次 jstack（JDK 17 实测，节选）：

```text
# 第一次
"idle-1" #14 prio=5 os_prio=31 cpu=0.10ms elapsed=3.20s ... waiting on condition
"order-calc" #17 prio=5 os_prio=31 cpu=3186.60ms elapsed=3.20s ... runnable

# 5 秒后
"idle-1" #14 prio=5 os_prio=31 cpu=0.14ms elapsed=8.31s ... waiting on condition
"order-calc" #17 prio=5 os_prio=31 cpu=8299.41ms elapsed=8.31s ... runnable
   java.lang.Thread.State: RUNNABLE
	at CpuHog.spin(CpuHog.java:22)
```

5 秒里 `order-calc` 用掉了约 5100ms 的 CPU，相当于一个核被它占满；`idle-1` 几乎没动。它的栈顶是 `CpuHog.spin` 方法里的那个死循环，问题定位到了代码行。

**CPU 飙高的常见原因**：

- 死循环，或者正则表达式的回溯爆炸。
- **频繁 GC**：GC 线程占满 CPU。`top -H` 里看到名为 `GC Thread#0` 这类的线程最忙时，转去看 jstat 和 GC 日志。
- 大量计算、序列化，或者锁竞争激烈时线程反复自旋。

Arthas 的 `thread -n 3` 能直接列出最忙的 3 个线程和它们的栈，线上排查很方便。

## 9.5 频繁 GC 与堆外内存

**Young GC 频繁**：对象分配得太快，或者新生代太小。先看是不是代码里产生了大量不必要的临时对象（比如循环里拼字符串、反复装箱），再考虑增大新生代。

**Full GC 频繁**：老年代总是被填满。按可能性排查：

- 内存泄漏（9.3 节）：回收后老年代水位越来越高。
- 新生代太小，对象还没来得及死就被提前晋升到老年代（6.3 节）。
- 大对象太多，直接进入老年代。
- 元空间不足。
- 代码或第三方库调用了 `System.gc()`。可以用 `-XX:+DisableExplicitGC` 禁掉，但要小心：直接内存用满时，JDK 会调用 `System.gc()` 来回收那些 DirectByteBuffer（2.5 节），禁掉后可能变成直接内存溢出。

**堆没满，进程内存却很高**：Java 进程占的内存远不止堆。给 LeakDemo 加上 `-XX:NativeMemoryTracking=summary`，用 `jcmd <pid> VM.native_memory summary` 查看（JDK 17 实测，节选）：

```text
Total: reserved=1751798KB, committed=267510KB
-                 Java Heap (reserved=262144KB, committed=140288KB)
-                     Class (reserved=1048664KB, committed=216KB)
-                    Thread (reserved=49498KB, committed=49498KB)
-                      Code (reserved=247738KB, committed=7626KB)
-                        GC (reserved=59835KB, committed=55451KB)
-        Shared class space (reserved=16384KB, committed=12176KB)
```

`committed` 是实际占用的部分。进程一共占了约 261MB，堆只占 137MB；GC 自己的数据结构（G1 的记忆集、标记位图等，7.5 节）占了 54MB，线程栈占了 48MB，代码缓存、类元数据也各占一些。此外还可能有直接内存，以及 JNI 库自己 malloc 的内存。

所以在容器里，**`-Xmx` 不能设成容器的内存上限**，否则堆没满，进程就因为超出容器限制被系统杀掉。常见做法是把堆设成容器内存的 70% 到 75%，或者用 `-XX:MaxRAMPercentage=75.0` 让 JVM 按比例自动计算（JDK 8u191 及以后、JDK 10 及以后能识别容器的内存限制）。

## 9.6 调优思路

**先排查，后调参**。大多数“JVM 问题”其实是代码问题：泄漏、不必要的对象、过大的查询结果。调参之前先回答三个问题：

1. **是不是 GC 的问题**？看 GC 日志和 jstat，用数据说话，而不是感觉“可能是 GC”。
2. **目标是什么**？吞吐量、停顿时间、还是内存占用？三者要取舍（7.1 节）。
3. **改一处，测一次**：一次只改一个参数，在同样的压力下对比改动前后的指标。

**常用参数**：

| 参数 | 作用 |
| --- | --- |
| `-Xms`、`-Xmx` | 初始堆、最大堆，服务端常设成一样 |
| `-Xmn` | 新生代大小（用 G1 时不建议固定，会干扰它的停顿目标） |
| `-Xss` | 每个线程的栈大小 |
| `-XX:MetaspaceSize`、`-XX:MaxMetaspaceSize` | 元空间第一次触发 GC 的阈值、元空间上限 |
| `-XX:MaxDirectMemorySize` | 直接内存上限 |
| `-XX:+UseG1GC`、`-XX:+UseZGC` | 选择收集器 |
| `-XX:MaxGCPauseMillis` | G1 的停顿时间目标 |
| `-XX:+HeapDumpOnOutOfMemoryError`、`-XX:HeapDumpPath` | OOM 时自动转储堆 |
| `-Xlog:gc*:file=...` | 打开 GC 日志 |
| `-XX:MaxRAMPercentage` | 在容器里按内存比例设置最大堆 |

想知道一个参数的默认值和最终生效值，用 `java -XX:+PrintFlagsFinal -version | grep <参数名>`。2.2 节的默认堆大小、1.4 节的默认栈大小，都是这样查出来的。

## 面试这样说

**问：线上 CPU 飙高怎么排查？**

> 先用 top 找到 CPU 最高的 Java 进程，再用 top -Hp 找到进程里 CPU 最高的线程，把线程号转成十六进制，在 jstack 输出里按 nid 找到这个线程，看它的栈停在哪段代码。最好隔几秒多打几次 jstack，确认一直停在同一个地方；JDK 11 以后 jstack 直接带每个线程的 cpu 时间，对比两次就能找到最忙的线程。如果最忙的是 GC 线程，就转去看 jstat 和 GC 日志，按频繁 GC 来查。常见原因有死循环、正则回溯、频繁 GC、锁竞争自旋。

**问：线上 OOM 或者内存泄漏怎么排查？**

> 先看 OOM 的提示，确认是堆、元空间、直接内存还是线程数的问题。如果是堆，用 jstat 或 GC 日志看老年代在 GC 后的水位，正常是锯齿形，泄漏是一路上涨；然后取证，两次类直方图对比看哪个类在涨，或者用 HeapDumpOnOutOfMemoryError 保留的堆转储，用 MAT 看 Dominator Tree 找占内存最多的对象，再看 Path to GC Roots 找谁引用着它。常见原因是静态集合当缓存没淘汰、ThreadLocal 没 remove、资源没关闭。生产环境 dump 堆会停顿、文件大、含敏感数据，最好先摘流量。

**问：JVM 调优一般从哪些方面入手？**

> 我会先确认问题是不是真的出在 GC，用 GC 日志和 jstat 看频率、停顿和回收后的水位，大多数时候根因在代码，比如泄漏或者创建了过多临时对象。确认是 GC 问题后，先明确目标是吞吐、停顿还是内存，再调整：堆大小设成固定值、容器里给堆外内存留足空间；按延迟要求选收集器，一般服务用 G1 设停顿目标，延迟敏感的大堆用 ZGC；新生代太小导致过早晋升就调大新生代。每次只改一个参数，在相同压力下对比指标。

## 自测

- [ ] `jstat -gcutil` 输出里，O、YGC、FGC 列分别是什么？怎么从中看出内存泄漏？
- [ ] 读一下这行日志：`GC(0) Pause Young (Normal) (G1 Evacuation Pause) 110M->111M(2048M) 16.331ms`。
- [ ] CPU 飙高时，怎么从进程一路定位到具体的代码行？
- [ ] 堆转储和线程转储有什么区别？生产环境转储堆要注意什么？
- [ ] 用 MAT 分析内存泄漏时，主要看哪几样东西？
- [ ] Java 进程的内存为什么会比 -Xmx 大很多？容器里 -Xmx 该怎么设？
- [ ] 为什么不建议一上来就调 JVM 参数？
