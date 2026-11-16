import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * 逃逸分析与标量替换（手册 8.3 节）：Point 只在方法里用，没有“逃”出去，
 * C2 编译后会把它拆成两个局部变量，根本不在堆上分配。
 *   java -Xmx64m EscapeDemo
 *   java -Xmx64m -XX:-DoEscapeAnalysis EscapeDemo
 */
public class EscapeDemo {
    static class Point {
        final int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }

    static long distance(int i) {
        Point p = new Point(i, i + 1);   // 没有逃逸：没存到字段里，也没 return 出去
        return (long) p.x * p.x + (long) p.y * p.y;
    }

    static long gcCount() {
        long n = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) n += Math.max(0, b.getCollectionCount());
        return n;
    }

    public static void main(String[] args) {
        long sum = 0;
        for (int round = 1; round <= 3; round++) {
            long gc0 = gcCount(), t0 = System.nanoTime();
            for (int i = 0; i < 200_000_000; i++) sum += distance(i);
            System.out.printf("第 %d 轮：2 亿次 new Point，用时 %4d ms，期间 GC %3d 次%n", round, (System.nanoTime() - t0) / 1_000_000, gcCount() - gc0);
        }
        System.out.println(sum);
    }
}
