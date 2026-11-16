import java.lang.instrument.Instrumentation;

/** 只做一件事：把 JVM 传进来的 Instrumentation 存起来，好调用 getObjectSize。 */
public class SizeAgent {
    public static Instrumentation inst;
    public static void premain(String args, Instrumentation i) { inst = i; }
}
