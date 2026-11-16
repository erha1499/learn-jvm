import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 直接内存溢出（手册 2.5 节）：DirectByteBuffer 的数据不在堆里，受 -XX:MaxDirectMemorySize 限制。
 *   java -Xmx1g -XX:MaxDirectMemorySize=10m DirectOOM
 */
public class DirectOOM {
    public static void main(String[] args) {
        List<ByteBuffer> list = new ArrayList<>();
        try {
            while (true) {
                list.add(ByteBuffer.allocateDirect(1024 * 1024));
            }
        } catch (OutOfMemoryError e) {
            System.out.println("申请了 " + list.size() + " MB 直接内存后：" + e);
        }
    }
}
