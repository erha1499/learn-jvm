/**
 * 类什么时候初始化（手册 3.1 节）：静态代码块打印了，就说明这个类初始化了。
 */
public class InitTiming {
    static class Parent {
        static int money = 100;
        static { System.out.println("    [Parent 初始化]"); }
    }

    static class Child extends Parent {
        static { System.out.println("    [Child 初始化]"); }
    }

    static class Const {
        static final String NAME = "jvm";            // 编译期常量
        static final long NOW = System.nanoTime();   // 运行时才知道值，不是编译期常量
        static { System.out.println("    [Const 初始化]"); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("1. 通过子类读父类的静态字段 Child.money");
        int m = Child.money;
        System.out.println("2. 创建数组 new Child[3]");
        Child[] arr = new Child[3];
        System.out.println("3. 读编译期常量 Const.NAME = " + Const.NAME);
        System.out.println("4. 读非编译期常量 Const.NOW");
        long t = Const.NOW;
        System.out.println("5. new Child()");
        new Child();
        System.out.println("6. 同一个类不会初始化第二次：再 new Child()");
        new Child();
    }
}
