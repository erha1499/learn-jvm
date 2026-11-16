/**
 * 一个最简单的方法，用来看字节码和栈帧（手册 1.2、1.3 节）。
 *   javac Calc.java && javap -c -v Calc
 */
public class Calc {
    static int add(int a, int b) {
        int c = a + b;
        return c * 2;
    }

    public static void main(String[] args) {
        System.out.println(add(3, 4));
    }
}
