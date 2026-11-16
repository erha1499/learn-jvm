import java.util.ArrayList;
import java.util.List;

/**
 * 堆溢出（手册 2.6 节）：对象一直被 list 引用着，GC 回收不掉，堆满了就抛 OOM。
 *   java -Xmx20m -XX:+HeapDumpOnOutOfMemoryError HeapOOM
 */
public class HeapOOM {
    public static void main(String[] args) {
        List<byte[]> list = new ArrayList<>();
        try {
            while (true) {
                list.add(new byte[100 * 1024]);  // 每次 100KB
            }
        } catch (OutOfMemoryError e) {
            int kb = list.size() * 100;
            list = null;  // 先释放，后面打印才有内存可用
            System.out.println("攒了 " + kb / 1024 + " MB 后：" + e);
        }
    }
}
