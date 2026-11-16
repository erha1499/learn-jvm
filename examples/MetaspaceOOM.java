import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 元空间溢出（手册 2.3 节）：每个新的类加载器都把同一份字节码再定义一次，
 * 得到一个新的类，类的元数据放在元空间里；加载器都被 list 引用着，类卸载不掉。
 *   java -XX:MaxMetaspaceSize=16m MetaspaceOOM
 */
public class MetaspaceOOM {
    public static class Tiny { }

    static class OneShotLoader extends ClassLoader {
        Class<?> define(byte[] b) { return defineClass(null, b, 0, b.length); }
    }

    public static void main(String[] args) throws Exception {
        byte[] bytes;
        try (InputStream in = MetaspaceOOM.class.getResourceAsStream("MetaspaceOOM$Tiny.class")) {
            bytes = in.readAllBytes();
        }
        List<Class<?>> keep = new ArrayList<>();
        try {
            while (true) {
                keep.add(new OneShotLoader().define(bytes));
            }
        } catch (OutOfMemoryError e) {
            int n = keep.size();
            keep = null;
            System.out.println("定义了 " + n + " 个类后：" + e);
        }
    }
}
