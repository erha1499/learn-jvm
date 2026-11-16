import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * 强、软、弱、虚四种引用在 GC 时的区别（手册 5.3 节）。
 *   java -Xmx64m RefTypes
 */
public class RefTypes {
    static final int MB = 1024 * 1024;

    public static void main(String[] args) throws Exception {
        ReferenceQueue<byte[]> queue = new ReferenceQueue<>();
        byte[] strong = new byte[8 * MB];
        SoftReference<byte[]> soft = new SoftReference<>(new byte[8 * MB]);
        WeakReference<byte[]> weak = new WeakReference<>(new byte[8 * MB]);
        PhantomReference<byte[]> phantom = new PhantomReference<>(new byte[8 * MB], queue);

        System.out.println("GC 之前  强=" + (strong != null) + "  软=" + (soft.get() != null) + "  弱=" + (weak.get() != null) + "  虚 get()=" + phantom.get());
        System.gc();
        Thread.sleep(100);
        System.out.println("一次 GC  强=" + (strong != null) + "  软=" + (soft.get() != null) + "  弱=" + (weak.get() != null) + "  虚引用进队列=" + (queue.poll() == phantom));

        List<byte[]> filler = new ArrayList<>();
        try {
            while (soft.get() != null) filler.add(new byte[4 * MB]);  // 一直分配，直到内存吃紧
            System.out.println("内存吃紧（又分配了 " + filler.size() * 4 + " MB）后  软=" + (soft.get() != null));
        } catch (OutOfMemoryError e) {
            filler = null;
            System.out.println("OOM 了，此时软=" + (soft.get() != null));
        }
        Reference.reachabilityFence(strong);
    }
}
