/**
 * 一次 Minor GC 里发生了什么（手册 6.3 节）。用 Serial 收集器、固定的小堆，日志最容易读：
 *   java -XX:+UseSerialGC -Xms24m -Xmx24m -Xmn10m -Xlog:gc,gc+heap=debug MinorGC
 * 新生代 10MB = Eden 8MB + 两个 Survivor 各 1MB，老年代 14MB。
 */
public class MinorGC {
    static final int MB = 1024 * 1024;

    public static void main(String[] args) {
        byte[] a1 = new byte[2 * MB];
        byte[] a2 = new byte[2 * MB];
        byte[] a3 = new byte[2 * MB];
        System.out.println("---- 已分配 a1、a2、a3 共 6MB，接下来分配 4MB 的 a4");
        byte[] a4 = new byte[4 * MB];   // Eden 放不下了，触发 Minor GC
        System.out.println("---- a4 分配完成");
    }
}
