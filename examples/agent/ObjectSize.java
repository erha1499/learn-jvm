/**
 * 对象到底占多少字节（手册 4.3 节）。需要先打成 Java Agent，运行方法见 examples/README.md。
 *   java -javaagent:agent.jar ObjectSize
 *   java -javaagent:agent.jar -XX:-UseCompressedOops ObjectSize
 */
public class ObjectSize {
    static class Point { int x; int y; }
    static class Mixed { byte b; long l; int i; Object o; }

    static void show(String label, Object o) {
        System.out.printf("%-22s %3d 字节%n", label, SizeAgent.inst.getObjectSize(o));
    }

    public static void main(String[] args) {
        show("new Object()", new Object());
        show("Integer", Integer.valueOf(1000));
        show("Long", Long.valueOf(1000));
        show("new Point() 两个 int", new Point());
        show("new Mixed()", new Mixed());
        show("new int[0]", new int[0]);
        show("new int[3]", new int[3]);
        show("new long[3]", new long[3]);
        show("String \"hi\" 本身", "hi");
    }
}
