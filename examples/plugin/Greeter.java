/** TwoLoaders 用的插件类，单独编译到 plugin/ 目录，不在 classpath 上。 */
public class Greeter {
    static { System.out.println("    [Greeter 被 " + Greeter.class.getClassLoader().getName() + " 初始化]"); }
    public String hi() { return "hi"; }
}
