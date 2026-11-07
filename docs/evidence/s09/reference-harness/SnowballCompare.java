import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

// Stems every word of upstream's frequency lists with upstream's Java Snowball (one class loader) and the port's
// Kotlin conversion (another), and counts the words where the two disagree.
public class SnowballCompare {
    public static void main(String[] a) throws Exception {
        URL javaStemmers = Paths.get(a[0]).toUri().toURL();
        URL[] portJars = Arrays.stream(a[1].split(File.pathSeparator)).map(p -> { try { return Paths.get(p).toUri().toURL(); } catch (Exception e) { throw new RuntimeException(e); } }).toArray(URL[]::new);
        ClassLoader upstream = new URLClassLoader(new URL[] { javaStemmers }, null);
        ClassLoader port = new URLClassLoader(portJars, null);
        Set<String> words = new TreeSet<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(Paths.get(a[2]))) {
            for (Path f : files) for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                int space = line.lastIndexOf(' ');
                String w = (space > 0 ? line.substring(0, space) : line).trim().toLowerCase(Locale.ROOT);
                if (!w.isEmpty() && !w.startsWith("#")) words.add(w);
            }
        }
        long total = 0, diff = 0;
        for (String lang : new String[] { "english", "spanish", "portuguese", "french", "german", "italian", "russian" }) {
            Object j = upstream.loadClass("org.tartarus.snowball.ext." + lang + "Stemmer").getConstructor().newInstance();
            Object k = port.loadClass("org.tartarus.snowball.ext." + lang + "Stemmer").getConstructor().newInstance();
            long d = 0;
            for (String w : words) {
                String sj = stem(j, w), sk = stem(k, w), sj2 = stem(j, sj), sk2 = stem(k, sk);
                if (!sj.equals(sk) || !sj2.equals(sk2)) { if (d < 5) System.out.println(lang + " " + w + " java=" + sj + " kotlin=" + sk); d++; }
            }
            System.out.println(lang + ": " + words.size() + " words, " + d + " differ");
            total += words.size(); diff += d;
        }
        System.out.println("TOTAL " + total + " stems, " + diff + " differ");
    }

    static String stem(Object s, String w) throws Exception {
        s.getClass().getMethod("setCurrent", String.class).invoke(s, w);
        s.getClass().getMethod("stem").invoke(s);
        return (String) s.getClass().getMethod("getCurrent").invoke(s);
    }
}
