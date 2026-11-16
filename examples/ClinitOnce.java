/**
 * 类初始化是线程安全的（手册 3.4 节）：两个线程同时触发初始化，静态代码块只执行一次，
 * 后到的线程等前一个初始化完。
 */
public class ClinitOnce {
    static class Slow {
        static {
            System.out.println("  " + Thread.currentThread().getName() + " 开始执行静态代码块，睡 1 秒");
            try { Thread.sleep(Long.getLong("ms", 1000)); } catch (InterruptedException e) { }
            System.out.println("  " + Thread.currentThread().getName() + " 静态代码块执行完");
        }
        static void hi() { System.out.println("  " + Thread.currentThread().getName() + " 用上了 Slow"); }
    }

    public static void main(String[] args) throws Exception {
        Runnable r = Slow::hi;
        Thread t1 = new Thread(r, "T1"), t2 = new Thread(r, "T2");
        t1.start();
        Thread.sleep(100);
        t2.start();
        Thread.sleep(300);
        System.out.println("  初始化进行中，T2 的状态：" + t2.getState());
        t1.join();
        t2.join();
    }
}
