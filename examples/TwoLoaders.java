import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 自定义类加载器 + “同名不同类”（手册 3.6 节）。
 * 先编译插件类：javac -d plugin plugin/Greeter.java
 * 再运行：      java TwoLoaders
 */
public class TwoLoaders {
    /** 只重写 findClass：先按双亲委派问父加载器，父加载器找不到才轮到这里。 */
    static class DirLoader extends ClassLoader {
        private final Path dir;
        DirLoader(String name, Path dir) { super(name, TwoLoaders.class.getClassLoader()); this.dir = dir; }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                byte[] b = Files.readAllBytes(dir.resolve(name.replace('.', '/') + ".class"));
                return defineClass(name, b, 0, b.length);
            } catch (java.io.IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of("plugin");
        DirLoader l1 = new DirLoader("loader-1", dir), l2 = new DirLoader("loader-2", dir);

        Class<?> c1 = l1.loadClass("Greeter");
        Class<?> c2 = l2.loadClass("Greeter");
        Object g1 = c1.getDeclaredConstructor().newInstance();
        c2.getDeclaredConstructor().newInstance();

        System.out.println("类名都是 " + c1.getName() + " 和 " + c2.getName());
        System.out.println("c1 == c2             : " + (c1 == c2));
        System.out.println("c2.isInstance(g1)    : " + c2.isInstance(g1));
        System.out.println("l1 再 loadClass 一次  : " + (l1.loadClass("Greeter") == c1));
        System.out.println("让 l1 加载 String     : " + l1.loadClass("java.lang.String").getClassLoader());
        try {
            c2.cast(g1);
        } catch (ClassCastException e) {
            System.out.println("c2.cast(g1)          : " + e.getMessage());
        }
    }
}
