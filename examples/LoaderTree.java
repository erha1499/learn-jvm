/**
 * 三层类加载器（手册 3.5 节）：打印几个类分别是谁加载的，以及加载器的父子链。
 * 启动类加载器是 C++ 实现的，在 Java 里表示为 null。
 */
public class LoaderTree {
    public static void main(String[] args) throws Exception {
        System.out.println("String           ← " + String.class.getClassLoader());
        System.out.println("java.sql.Driver  ← " + Class.forName("java.sql.Driver").getClassLoader());
        System.out.println("LoaderTree       ← " + LoaderTree.class.getClassLoader());
        System.out.println();
        ClassLoader cl = LoaderTree.class.getClassLoader();
        while (cl != null) {
            System.out.println(cl + " 的 parent 是 " + cl.getParent());
            cl = cl.getParent();
        }
    }
}
