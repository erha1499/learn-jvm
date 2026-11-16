/**
 * new 一个对象对应哪几条字节码（手册 4.1 节）：javap -c NewObject
 */
public class NewObject {
    int x;
    NewObject(int x) { this.x = x; }

    public static void main(String[] args) {
        NewObject o = new NewObject(42);
        System.out.println(o.x);
    }
}
