import sun.misc.Unsafe;
import java.lang.reflect.Field;

/**
 * 字段在对象里的位置（手册 4.3 节）：用 Unsafe 读出每个字段的偏移量。
 * 对象头之后是字段，JVM 会重新排列字段的顺序，让对象更紧凑。
 *   java FieldLayout
 *   java -XX:-UseCompressedClassPointers -XX:-UseCompressedOops FieldLayout
 */
public class FieldLayout {
    static class Mixed { byte b; long l; int i; Object o; }

    public static void main(String[] args) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Unsafe u = (Unsafe) f.get(null);
        System.out.println("源码里的声明顺序：byte b; long l; int i; Object o;");
        for (String name : new String[]{"b", "l", "i", "o"}) {
            System.out.println("  " + name + " 的偏移量 = " + u.objectFieldOffset(Mixed.class.getDeclaredField(name)));
        }
    }
}
