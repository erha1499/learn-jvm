/**
 * TLAB 有多大用（手册 4.2 节）：4 个线程同时分配小对象，对比打开和关闭 TLAB 的耗时。
 *   java TlabBench
 *   java -XX:-UseTLAB TlabBench
 */
public class TlabBench {
    static final int THREADS = 4, PER_THREAD = 20_000_000;
    static final Object[] SINK = new Object[THREADS * 64];   // 存一下，防止分配被优化掉

    public static void main(String[] args) throws Exception {
        for (int round = 1; round <= 3; round++) {
            Thread[] ts = new Thread[THREADS];
            long start = System.nanoTime();
            for (int t = 0; t < THREADS; t++) {
                final int slot = t * 64;
                ts[t] = new Thread(() -> {
                    for (int i = 0; i < PER_THREAD; i++) SINK[slot] = new byte[16];
                });
                ts[t].start();
            }
            for (Thread t : ts) t.join();
            System.out.printf("第 %d 轮：%d 个线程各分配 %,d 个对象，用时 %d ms%n", round, THREADS, PER_THREAD, (System.nanoTime() - start) / 1_000_000);
        }
    }
}
