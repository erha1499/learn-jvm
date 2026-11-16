/**
 * loadClass 只加载，Class.forName 默认会初始化（手册 3.3 节）。
 */
public class LoadVsInit {
    static class Hello {
        static { System.out.println("    [Hello 初始化]"); }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader app = LoadVsInit.class.getClassLoader();
        System.out.println("1. app.loadClass(\"LoadVsInit$Hello\")");
        Class<?> c = app.loadClass("LoadVsInit$Hello");
        System.out.println("2. Class.forName(\"LoadVsInit$Hello\")");
        Class.forName("LoadVsInit$Hello");
    }
}
