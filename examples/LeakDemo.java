import java.util.HashMap;
import java.util.Map;

/**
 * 模拟内存泄漏（手册 9.3 节）：每个“请求”都往静态 Map 里放一条记录，却从来不删。
 * 运行后用 jps 找到进程号，再用 jstat、jcmd 观察：
 *   java -Xmx256m LeakDemo
 *   jstat -gcutil <进程号> 1000
 *   jcmd <进程号> GC.class_histogram | head
 */
public class LeakDemo {
    static final Map<String, byte[]> CACHE = new HashMap<>();   // 只进不出的“缓存”

    static void handleRequest(long id) {
        CACHE.put("session-" + id, new byte[1024]);   // 每次请求留下 1KB
    }

    public static void main(String[] args) throws Exception {
        long id = 0;
        while (true) {
            for (int i = 0; i < 200; i++) handleRequest(id++);   // 每秒约 2000 个请求
            Thread.sleep(100);
        }
    }
}
