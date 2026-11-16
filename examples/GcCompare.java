import java.util.concurrent.ThreadLocalRandom;

/**
 * 同一份负载换不同的收集器跑，看业务线程最长被卡住多久（手册 7.7 节）。
 * 负载：堆里常驻约 400MB 小对象，不停地随机替换其中一部分（制造老年代垃圾），同时大量分配短命对象。
 * 每做 1000 次操作记一次耗时；正常一批不到 0.1ms，慢的那几批就是撞上了 GC 停顿。
 *   java -Xms2g -Xmx2g -XX:+UseSerialGC   -Xlog:gc GcCompare
 *   java -Xms2g -Xmx2g -XX:+UseParallelGC -Xlog:gc GcCompare
 *   java -Xms2g -Xmx2g -XX:+UseG1GC       -Xlog:gc GcCompare
 *   java -Xms2g -Xmx2g -XX:+UseZGC        -Xlog:gc,gc+phases GcCompare
 */
public class GcCompare {
    static final int LIVE = 2_000_000, SECONDS = 10;
    static volatile Object sink;

    public static void main(String[] args) {
        Object[] live = new Object[LIVE];
        for (int i = 0; i < LIVE; i++) live[i] = new byte[184];   // 每个约 200 字节，共约 400MB

        long worst = 0, slow = 0, end = System.currentTimeMillis() + SECONDS * 1000L;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        while (System.currentTimeMillis() < end) {
            long t0 = System.nanoTime();
            for (int k = 0; k < 1000; k++) {
                sink = new byte[r.nextInt(16, 256)];       // 短命对象
                live[r.nextInt(LIVE)] = new byte[184];   // 替换一个常驻对象，旧的变成老年代垃圾
            }
            long cost = System.nanoTime() - t0;
            worst = Math.max(worst, cost);
            if (cost > 10_000_000) slow++;
        }
        System.out.printf("业务线程最长卡顿 %.1f ms，超过 10ms 的卡顿 %d 次%n", worst / 1e6, slow);
    }
}
