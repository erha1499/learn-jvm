/**
 * 字符串常量池与 intern（手册 2.4 节）。JDK 7 起字符串常量池在堆里，intern 只记录引用。
 */
public class StringPool {
    public static void main(String[] args) {
        String a = "hello";
        String b = "hello";
        String c = new String("hello");
        System.out.println("1. \"hello\" == \"hello\"            : " + (a == b));
        System.out.println("2. new String(\"hello\") == \"hello\": " + (c == a));
        System.out.println("3. c.intern() == \"hello\"         : " + (c.intern() == a));

        String s1 = new String("ja") + new String("vm");   // 堆里有 "javm"，常量池里还没有
        String s2 = s1.intern();                             // 常量池记下 s1 这个对象的引用
        System.out.println("4. s1.intern() == s1             : " + (s2 == s1));
        System.out.println("5. \"javm\" == s1                  : " + ("javm" == s1));

        String s3 = new String("he") + new String("llo");  // "hello" 早就在常量池里了
        System.out.println("6. s3.intern() == s3             : " + (s3.intern() == s3));

        final String x = "hel";
        String y = "hel";
        System.out.println("7. (final x + \"lo\") == \"hello\"   : " + ((x + "lo") == a));
        System.out.println("8. (y + \"lo\") == \"hello\"         : " + ((y + "lo") == a));
    }
}
