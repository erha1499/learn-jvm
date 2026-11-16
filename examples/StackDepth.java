/**
 * 递归多深会 StackOverflowError（手册 1.4 节）。
 * 栈帧越大（局部变量越多），同样大小的栈能放下的帧越少。
 *   java StackDepth
 *   java -Xss256k StackDepth
 *   java -Xss4m StackDepth
 */
public class StackDepth {
    static int depth = 0;

    static void small() {
        depth++;
        small();
    }

    static void big() {
        long a = 1, b = 2, c = 3, d = 4, e = 5, f = 6, g = 7, h = 8;
        depth++;
        big();
        if (a + b + c + d + e + f + g + h == 0) System.out.println();  // 让局部变量不被优化掉
    }

    public static void main(String[] args) {
        try { small(); } catch (StackOverflowError err) { System.out.println("没有局部变量：递归 " + depth + " 层后 StackOverflowError"); }
        depth = 0;
        try { big(); } catch (StackOverflowError err) { System.out.println("8 个 long 局部变量：递归 " + depth + " 层后 StackOverflowError"); }
    }
}
