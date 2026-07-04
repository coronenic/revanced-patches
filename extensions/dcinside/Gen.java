import java.lang.reflect.*;
import java.net.*;
import java.util.*;

// Generates a PackageManager delegate subclass, correct-by-construction from android.jar.
public class Gen {
    static String src(Type t) {
        // Source-renderable type name: nested '$' -> '.'
        return t.getTypeName().replace('$', '.');
    }
    public static void main(String[] args) throws Exception {
        URLClassLoader cl = new URLClassLoader(new URL[]{ new java.io.File(args[0]).toURI().toURL() });
        Class<?> pm = Class.forName("android.content.pm.PackageManager", false, cl);
        StringBuilder b = new StringBuilder();
        b.append("package app.revanced.extension.dcinside;\n\n");
        b.append("// AUTO-GENERATED from android.jar (API 35) by Gen.java. Do not hand-edit.\n");
        b.append("// Delegates every PackageManager abstract method to the real instance;\n");
        b.append("// only getPackageInfo(...) is intercepted to inject the original signature.\n");
        b.append("@SuppressWarnings({\"unchecked\",\"deprecation\",\"rawtypes\"})\n");
        b.append("public final class SpoofPackageManager extends android.content.pm.PackageManager {\n");
        b.append("    private final android.content.pm.PackageManager real;\n");
        b.append("    public SpoofPackageManager(android.content.pm.PackageManager real) { this.real = real; }\n\n");

        // De-duplicate by erased signature (name + param raw types)
        LinkedHashMap<String, Method> uniq = new LinkedHashMap<>();
        for (Method m : pm.getMethods()) {
            if (!Modifier.isAbstract(m.getModifiers())) continue;
            StringBuilder key = new StringBuilder(m.getName());
            for (Class<?> p : m.getParameterTypes()) key.append('|').append(p.getName());
            uniq.putIfAbsent(key.toString(), m);
        }
        int count = 0;
        for (Method m : uniq.values()) {
            Type[] pts = m.getGenericParameterTypes();
            Class<?>[] praw = m.getParameterTypes();
            boolean varargs = m.isVarArgs();
            b.append("    public ").append(src(m.getGenericReturnType())).append(' ').append(m.getName()).append('(');
            StringBuilder call = new StringBuilder();
            for (int i = 0; i < pts.length; i++) {
                if (i > 0) { b.append(", "); call.append(", "); }
                String pt = src(pts[i]);
                if (varargs && i == pts.length - 1 && pt.endsWith("[]")) pt = pt.substring(0, pt.length() - 2) + "...";
                b.append(pt).append(" a").append(i);
                call.append("a").append(i);
            }
            b.append(')');
            Type[] exs = m.getGenericExceptionTypes();
            if (exs.length > 0) {
                b.append(" throws ");
                for (int i = 0; i < exs.length; i++) { if (i>0) b.append(", "); b.append(src(exs[i])); }
            }
            b.append(" {\n");
            boolean isVoid = m.getReturnType() == void.class;
            boolean isGetPkgInfo = m.getName().equals("getPackageInfo") && praw.length == 2 && praw[1] == int.class;
            if (isGetPkgInfo) {
                b.append("        return app.revanced.extension.dcinside.SignatureSpoof.maybeSpoof(real.")
                 .append(m.getName()).append('(').append(call).append("), a1);\n");
            } else {
                b.append("        ").append(isVoid ? "" : "return ").append("real.").append(m.getName())
                 .append('(').append(call).append(");\n");
            }
            b.append("    }\n");
            count++;
        }
        b.append("}\n");
        System.out.println(b);
        System.err.println("generated methods: " + count);
    }
}
