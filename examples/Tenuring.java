/**
 * 对象的年龄和晋升（手册 6.3 节）：一个一直活着的对象，每熬过一次 Minor GC 年龄加 1，
 * 到了阈值就搬进老年代。为了少等几轮，把阈值设成 3：
 *   java -XX:+UseSerialGC -Xms40m -Xmx40m -Xmn20m -XX:MaxTenuringThreshold=3 -Xlog:gc+age=trace,gc+heap=info Tenuring
 * 新生代 20MB = Eden 16MB + 两个 Survivor 各 2MB。
 */
public class Tenuring {
    public static void main(String[] args) {
        byte[] longLived = new byte[128 * 1024];   // 一直被引用着，每次 GC 都活下来
        for (int i = 1; i <= 5; i++) {
            for (int j = 0; j < 16; j++) {
                byte[] garbage = new byte[1024 * 1024];   // 用完就扔，把 Eden 填满触发 Minor GC
            }
            System.out.println("---- 第 " + i + " 轮分配结束");
        }
        System.out.println(longLived.length);
    }
}
