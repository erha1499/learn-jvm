/**
 * 准备阶段和初始化阶段（手册 3.2 节）：先全部清零，再按代码顺序执行赋值和静态代码块。
 */
public class PreparePuzzle {
    static class Counter {
        static Counter INSTANCE = new Counter();  // ① 最先执行：构造方法里两个计数各加 1
        static int a;                              // ② 没有赋值语句，什么都不做
        static int b = 0;                          // ③ 把 b 重新赋成 0

        Counter() {
            a++;
            b++;
        }
    }

    public static void main(String[] args) {
        System.out.println("a = " + Counter.a + ", b = " + Counter.b);
    }
}
