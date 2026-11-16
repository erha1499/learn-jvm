import java.lang.ref.WeakReference;

/**
 * 循环引用的对象也会被回收（手册 5.1 节）：JVM 用的是可达性分析，不是引用计数。
 * 用弱引用当“探针”：对象被回收后，弱引用 get() 会返回 null。
 *   java -Xlog:gc CycleRef
 */
public class CycleRef {
    static class Node {
        Node other;
        byte[] payload = new byte[10 * 1024 * 1024];  // 10MB，方便在 GC 日志里看出来
    }

    public static void main(String[] args) {
        Node a = new Node(), b = new Node();
        a.other = b;
        b.other = a;   // a 和 b 互相引用，引用计数都是 2
        WeakReference<Node> probeA = new WeakReference<>(a), probeB = new WeakReference<>(b);

        a = null;
        b = null;      // 去掉外部引用后，计数各剩 1，但从 GC Roots 已经走不到它们
        System.gc();

        System.out.println("a 被回收了吗：" + (probeA.get() == null));
        System.out.println("b 被回收了吗：" + (probeB.get() == null));
    }
}
