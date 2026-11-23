# 单元三 类加载：时机、过程与双亲委派

本单元的核心：类加载把 `.class` 里的字节变成方法区里的类型信息和堆里的 `Class` 对象；它是“懒”的，第一次真正用到才初始化，而“由哪个类加载器加载”也是类身份的一部分。

**学完这个单元，你能：**

- 说出类加载的五个阶段，分清“准备”阶段的清零和“初始化”阶段的赋值。
- 判断一段代码会不会触发某个类的初始化。
- 讲清双亲委派的流程和好处，知道哪些场景要打破它。

单元二说类的信息放在方法区，本单元回答：类是什么时候、被谁、怎么放进去的。顺序是：先看一个静态代码块“该执行却没执行”的现象（3.1），再拆开类加载的各个阶段（3.2），总结哪些操作会触发初始化（3.3）和初始化的线程安全（3.4），然后认识类加载器（3.5）和双亲委派（3.6），最后看什么时候要打破它（3.7）。

## 3.1 先看现象：静态代码块什么时候执行

类的静态代码块在类**初始化**时执行，打印出来了就说明这个类初始化了。`examples/InitTiming.java` 里，Child 继承 Parent，`money` 是 Parent 的静态字段；Const 里有两个 `static final` 字段，一个是字面量，一个要运行时计算。JDK 17 实测输出：

```text
1. 通过子类读父类的静态字段 Child.money
    [Parent 初始化]
2. 创建数组 new Child[3]
3. 读编译期常量 Const.NAME = jvm
4. 读非编译期常量 Const.NOW
    [Const 初始化]
5. new Child()
    [Child 初始化]
6. 同一个类不会初始化第二次：再 new Child()
```

几个“没执行”的地方都值得注意：

- **第 1 步**：`money` 是 Parent 声明的，通过 Child 去读，只初始化 Parent，Child 不初始化。
- **第 2 步**：创建 Child 类型的数组，Child 不初始化。数组是 JVM 另外生成的一个类型。
- **第 3 步**：`NAME` 是编译期常量，javac 编译时就把 `"jvm"` 直接抄进了 InitTiming 自己的常量池，运行时根本没碰 Const。
- **第 4 步**：`NOW` 虽然也是 `static final`，但它的值要运行 `System.nanoTime()` 才知道，读它就要先初始化 Const。
- **第 6 步**：一个类只初始化一次。

## 3.2 类的生命周期

一个类从被加载到被卸载，要经过这些阶段，其中验证、准备、解析合称**链接**：

| 阶段 | 做什么 |
| --- | --- |
| 加载 | 按类的全限定名找到字节（文件、jar 包、网络、运行时生成都可以），在方法区建立类的结构，在堆里生成一个 `java.lang.Class` 对象作为访问入口 |
| 验证 | 检查字节码是否合法、安全：魔数、版本号、类型是否匹配、跳转有没有越界 |
| 准备 | 给**静态变量**分配内存，并设成**零值**（0、false、null）；编译期常量在这一步直接设成最终值 |
| 解析 | 把常量池里的符号引用（类名、方法名）换成直接引用（内存地址），可以推迟到第一次用到时再做 |
| 初始化 | 执行类构造器 `<clinit>()`：按源码顺序执行所有静态变量的赋值语句和静态代码块；父类的 `<clinit>()` 先执行 |
| 使用、卸载 | 卸载的条件很苛刻，见 5.4 节 |

最容易混的是**准备**和**初始化**：准备阶段只清零，初始化阶段才执行你写的赋值。`<clinit>()` 是编译器把所有静态赋值语句和静态代码块，按源码中出现的顺序拼成的一个方法。

**一道经典题**：`examples/PreparePuzzle.java`

```java
static class Counter {
    static Counter INSTANCE = new Counter();  // ①
    static int a;                              // ②
    static int b = 0;                          // ③

    Counter() {
        a++;
        b++;
    }
}
// main 里打印 Counter.a 和 Counter.b
```

JDK 17 实测输出：

```text
a = 1, b = 0
```

- **准备阶段**：INSTANCE = null，a = 0，b = 0。
- **初始化阶段**，按源码顺序执行 `<clinit>()`：
  - ① `new Counter()` 执行构造方法，a 变成 1，b 变成 1。
  - ② `static int a;` 没有赋值语句，`<clinit>()` 里没有它，a 保持 1。
  - ③ `b = 0` 把 b 重新赋成 0。

把 ① 挪到最后一行，结果就变成 `a = 1, b = 1`。

## 3.3 什么时候触发初始化

JVM 规范规定，只有下面几种“主动使用”才会触发类的初始化：

1. 执行 `new`、读写静态字段（编译期常量除外）、调用静态方法。对应字节码的 `new`、`getstatic`、`putstatic`、`invokestatic`。
2. 用反射调用，比如 `Class.forName("...")`。
3. 初始化一个类时，如果父类还没初始化，先初始化父类。
4. 虚拟机启动时，先初始化 `main` 方法所在的类。

除此以外的引用都不会触发初始化，比如 3.1 节的通过子类读父类静态字段、创建数组、读编译期常量。

还有一个常见的对比：**`ClassLoader.loadClass()` 只加载，不初始化；`Class.forName()` 默认会初始化**。`examples/LoadVsInit.java` 实测：

```text
1. app.loadClass("LoadVsInit$Hello")
2. Class.forName("LoadVsInit$Hello")
    [Hello 初始化]
```

这也是早年 JDBC 要写 `Class.forName("com.mysql.jdbc.Driver")` 的原因：驱动类在静态代码块里把自己注册到 DriverManager，必须触发初始化才行。

## 3.4 类初始化是线程安全的

JVM 保证一个类的 `<clinit>()` 只执行一次。多个线程同时触发初始化，只有一个线程去执行，其他线程等它执行完。

`examples/ClinitOnce.java` 让 T1 先触发初始化，静态代码块里睡 1 秒；100ms 后 T2 也来用这个类。JDK 17 实测：

```text
  T1 开始执行静态代码块，睡 1 秒
  初始化进行中，T2 的状态：RUNNABLE
  T1 静态代码块执行完
  T1 用上了 Slow
  T2 用上了 Slow
```

静态代码块只执行了一次，T2 一直等到 T1 初始化完才继续。所以静态代码块里不要做耗时操作，两个类的静态初始化也不要互相依赖，否则线程会被卡住，甚至死锁。

这个保证也给了一种不加锁的懒加载单例写法，**静态内部类单例**：

```java
public class Singleton {
    private Singleton() { }
    private static class Holder {
        static final Singleton INSTANCE = new Singleton();
    }
    public static Singleton getInstance() { return Holder.INSTANCE; }
}
```

外部类加载时 Holder 不会初始化；第一次调用 `getInstance()` 读 `Holder.INSTANCE`，才触发 Holder 初始化、创建实例，而 JVM 保证这一步只发生一次。

### 深入一层（可跳过）：等类初始化的线程在 jstack 里是什么样

上面的输出里，T2 在等待时状态是 **RUNNABLE**，而不是 BLOCKED 或 WAITING。用 jstack 看 T2（节选）：

```text
"T2" #15 prio=5 ... in Object.wait()
   java.lang.Thread.State: RUNNABLE
	at ClinitOnce$$Lambda$1/0x0000009801000c10.run(Unknown Source)
	- waiting on the Class initialization monitor for ClinitOnce$Slow
```

线上如果有线程卡在“等类初始化”上，监控里看到的是 RUNNABLE，很容易被当成在干活。`waiting on the Class initialization monitor` 这一行就是排查线索。

## 3.5 类加载器：三层

“加载”阶段由**类加载器**完成。JDK 9 之后内置三个：

| 类加载器 | 加载什么 | 在 Java 里看到的是 |
| --- | --- | --- |
| 启动类加载器（Bootstrap） | JDK 最核心的类，如 `java.lang.*`、`java.util.*` | `null`（它是 C++ 实现的，不是 Java 对象） |
| 平台类加载器（Platform） | 其余的 JDK 模块，如 `java.sql`。JDK 8 里对应的是扩展类加载器（Extension），加载 `jre/lib/ext` | `PlatformClassLoader` |
| 应用类加载器（Application） | classpath 上的类，也就是我们自己写的代码和依赖的 jar | `AppClassLoader` |

`examples/LoaderTree.java` 打印了三个类分别由谁加载，JDK 17 实测（节选）：

```text
String           ← null
java.sql.Driver  ← jdk.internal.loader.ClassLoaders$PlatformClassLoader@7644db57
LoaderTree       ← jdk.internal.loader.ClassLoaders$AppClassLoader@22d8cfe0
```

程序接着沿 `getParent()` 往上走：应用类加载器的 parent 是平台类加载器，平台类加载器的 parent 是 null（启动类加载器）。这里的“父子”不是继承关系，而是每个加载器内部有一个 `parent` 字段，指向它的上级。

还有一条后面反复要用的规则：**一个类的身份 = 全限定名 + 加载它的类加载器**。同一份 `.class` 文件，被两个不同的类加载器加载，在 JVM 看来就是两个不同的类。

## 3.6 双亲委派

类加载器收到加载请求时，不是自己先动手，而是按这个顺序来：

1. 先查自己是不是已经加载过这个类，加载过就直接返回。
2. 没加载过，先把请求**交给父加载器**；父加载器也照此办理，一路交到最顶上的启动类加载器。
3. 父加载器找不到这个类，才轮到自己去找（调用 `findClass`）。

就像新员工遇到问题先请示组长，组长再请示经理，上级能处理的上级处理；上级都处理不了，才一层层退回来自己办。`ClassLoader.loadClass` 的源码就是这三步（JDK 源码简化）：

```java
protected Class<?> loadClass(String name, boolean resolve) {
    synchronized (getClassLoadingLock(name)) {
        Class<?> c = findLoadedClass(name);                       // ① 加载过就直接返回
        if (c == null) {
            try {
                if (parent != null) c = parent.loadClass(name, false);  // ② 先交给父加载器
                else c = findBootstrapClassOrNull(name);                 //    顶层交给启动类加载器
            } catch (ClassNotFoundException e) { }
            if (c == null) c = findClass(name);                   // ③ 上级都找不到，才自己找
        }
        return c;
    }
}
```

**这么设计有两个好处**：

- **安全**：核心类永远由启动类加载器加载。你自己写一个 `java.lang.String` 放在 classpath 上，加载请求一路交到启动类加载器，它先找到了 JDK 的 String，你的那个根本轮不到。
- **不重复**：同一个类只要上级加载过，下级就直接用上级的那份，不会出现两个“不同的” `java.lang.Object`。

**自定义类加载器**：继承 `ClassLoader`，**重写 `findClass`**，负责“自己去找”这一步（从某个目录、网络或加密文件里读出字节，再调用 `defineClass`）。这样前两步仍由父类的 `loadClass` 完成，双亲委派保留下来。

`examples/TwoLoaders.java` 写了一个从 `plugin/` 目录读类的加载器，用两个实例 l1、l2 分别加载同一个 `Greeter.class`（它不在 classpath 上）。JDK 17 实测：

```text
    [Greeter 被 loader-1 初始化]
    [Greeter 被 loader-2 初始化]
类名都是 Greeter 和 Greeter
c1 == c2             : false
c2.isInstance(g1)    : false
l1 再 loadClass 一次  : true
让 l1 加载 String     : null
c2.cast(g1)          : Cannot cast Greeter to Greeter
```

- l1、l2 的父加载器是应用类加载器，它在 classpath 上找不到 Greeter，于是各自调用 findClass，得到**两个不同的类**。类名完全一样，`c1 == c2` 却是 false，类型转换报出 `Cannot cast Greeter to Greeter`。在 Tomcat 或热部署环境里看到这种报错，就要想到是两个类加载器各加载了一份。
- 让 l1 加载 String，请求一路交到启动类加载器，返回的是 JDK 的 String（加载器显示为 null）。

### 深入一层（可跳过）：JDK 9 模块化之后的委派

JDK 9 引入模块系统后，平台类加载器和应用类加载器的 `loadClass` 被改写过：收到请求时，先根据包名判断这个类属于哪个模块，如果属于某个已知模块，就直接交给负责那个模块的加载器，而不是机械地先问父加载器。核心类仍然只会由启动类加载器加载，所以双亲委派的两个好处没有变，学习时按 3.6 节的三步理解就够了。

## 3.7 什么时候要打破双亲委派

双亲委派有一个局限：**上级看不见下级的类**。启动类加载器只认识 JDK 核心类，classpath 上的类它找不到。下面几种场景因此要“打破”它：

- **SPI 与线程上下文类加载器**：JDBC 的 `DriverManager` 在 `java.sql` 模块里，由平台类加载器加载；而 MySQL 驱动在 classpath 上，平台类加载器看不见。解决办法是让 JDK 的代码拿到**线程上下文类加载器**（`Thread.currentThread().getContextClassLoader()`，默认就是应用类加载器），用它去加载驱动。相当于上级把任务反过来委托给下级。
- **Tomcat 的类隔离**：一个 Tomcat 里跑多个 Web 应用，它们可能依赖同一个库的不同版本。Tomcat 给每个应用一个独立的 `WebappClassLoader`，**先在自己的 `WEB-INF` 里找，找不到再交给上级**（JDK 核心类除外），这样各应用的类互不干扰。这需要重写 `loadClass`。
- **热部署**：一个类加载器不能把已经加载的类再加载一遍。要换成新版本的类，就丢掉旧的类加载器，new 一个新的加载器重新加载。旧的加载器和它加载的类没人引用了，才能被回收；如果一直被引用着，就会出现单元二的元空间溢出。

## 面试这样说

**问：讲一下类加载的过程。**

> 类的生命周期是加载、验证、准备、解析、初始化、使用、卸载，中间三个合称链接。加载是按全限定名找到字节流，在方法区建立类的结构，在堆里生成 Class 对象；验证检查字节码合法；准备给静态变量分配内存并设零值；解析把符号引用换成直接引用；初始化执行编译器生成的 `<clinit>`，按源码顺序执行静态赋值和静态代码块，父类先初始化。初始化是懒的，只有 new、读写静态字段、调静态方法、反射、初始化子类、启动主类这几种主动使用才会触发，而且 JVM 保证它线程安全、只执行一次。

**问：什么是双亲委派？为什么这样设计？怎么打破？**

> 类加载器收到请求，先查自己加载过没有，没有就委托给父加载器，一直到启动类加载器，父加载器找不到才自己加载。好处一是安全，核心类只能由启动类加载器加载，没法被同名类替换；二是避免重复加载。打破的场景主要有三个：JDBC 这类 SPI，核心库要加载 classpath 上的实现类，用线程上下文类加载器反向委托；Tomcat 为了隔离不同 Web 应用，让每个应用的加载器先自己加载；热部署要丢掉旧加载器重新加载。自定义加载器想保留双亲委派就重写 findClass，想打破就重写 loadClass。

## 自测

- [ ] 准备阶段和初始化阶段都会给静态变量赋值，有什么区别？
- [ ] PreparePuzzle 为什么输出 a = 1, b = 0？把 INSTANCE 那一行挪到最后会怎样？
- [ ] 通过子类访问父类的静态字段，子类会初始化吗？读一个 `static final String` 字面量常量呢？
- [ ] `Class.forName` 和 `ClassLoader.loadClass` 有什么区别？
- [ ] 两个类的全限定名相同，一定是同一个类吗？
- [ ] 自己写一个 `java.lang.String` 能替换掉 JDK 的吗？为什么？
- [ ] JDBC 加载驱动为什么要用线程上下文类加载器？
- [ ] 自定义类加载器应该重写 findClass 还是 loadClass？
