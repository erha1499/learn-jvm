/**
 * CPU 飙高的进程（手册 8.4 节）：有一个线程在死循环里空转，其他线程都在睡觉。
 *   java CpuHog
 *   jstack <进程号>   看每个线程的 cpu= 时间，隔几秒再看一次，涨得最快的就是它
 */
public class CpuHog {
    public static void main(String[] args) {
        for (int i = 1; i <= 3; i++) {
            new Thread(() -> {
                while (true) {
                    try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                }
            }, "idle-" + i).start();
        }
        new Thread(CpuHog::spin, "order-calc").start();
    }

    static void spin() {
        long x = 0;
        while (true) {
            x = x * 31 + System.nanoTime() % 7;   // 一直算，从不休息
            if (x == 42) System.out.println(x);
        }
    }
}
