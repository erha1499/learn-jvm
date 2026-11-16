/**
 * 同一段代码，越跑越快（手册 8.1 节）：前几批由解释器执行，热起来以后被 JIT 编译成机器码。
 *   java JitWarmup
 *   java -Xint JitWarmup              只用解释器
 *   java -XX:+PrintCompilation JitWarmup | grep JitWarmup   看哪些方法在什么时候被编译
 */
public class JitWarmup {
    static int work(int n) {
        int h = 0;
        for (int i = 0; i < n; i++) h = 31 * h + (i ^ (h >>> 7));
        return h;
    }

    public static void main(String[] args) {
        int sink = 0;
        long total = System.nanoTime();
        for (int batch = 1; batch <= 8; batch++) {
            long t0 = System.nanoTime();
            for (int k = 0; k < 200; k++) sink += work(1000);
            System.out.printf("第 %2d 批：%6d µs%n", batch, (System.nanoTime() - t0) / 1000);
        }
        System.out.printf("合计 %d ms（%d）%n", (System.nanoTime() - total) / 1_000_000, sink & 1);
    }
}
