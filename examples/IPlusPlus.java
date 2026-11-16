/**
 * i = i++ 为什么还是 0（手册 1.3 节）。
 * 用 javap -c IPlusPlus 查看字节码：iload 先把 i 的旧值压进操作数栈，iinc 只改局部变量表，
 * 最后 istore 又把栈上的旧值写回 i。
 */
public class IPlusPlus {
    public static void main(String[] args) {
        int i = 0;
        i = i++;
        System.out.println("i = i++ 之后，i = " + i);

        int j = 0;
        j = ++j;
        System.out.println("j = ++j 之后，j = " + j);
    }
}
