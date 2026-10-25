import java.util.*; import java.time.*; import java.time.format.*;
public class ReferenceRegression {
 static float width(String s,float size){return s.length()*size;}
 static String fit(String s,float room,float size){int n=(int)(room/size);return s.length()<=n?s:s.substring(0,Math.max(0,n-3))+"...";}
    private static List<String> lines(String text, float width, float size, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (line.isEmpty() || width(candidate, size) <= width) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            lines.add(line.toString());
            line.setLength(0);
            line.append(word);
            if (lines.size() == maxLines - 1) break;
        }
        if (lines.size() == maxLines - 1) {
            String rest = text.substring(Math.min(text.length(), String.join(" ", lines).length() + 1));
            lines.add(fit(rest, width, size));
        } else if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }


 public static void main(String[] args){
 String raw="Breaks\uE120the\uE120block\uE120in\uE120front\uE120of\uE120the\uE120target";
 List<String> before=lines(raw,20,1,2);
 if(before.size()!=1||before.get(0).length()<=20)throw new AssertionError();
 List<String> after=lines(raw.replace('\uE120',' '),20,1,2);
 for(String line:after)if(fit(line,20,1).length()>20)throw new AssertionError();
 if(after.size()!=2)throw new AssertionError();
 String date=LocalDateTime.of(2026,10,21,16,1).format(DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm",Locale.ENGLISH));
 if(!date.equals("21 Oct 2026, 16:01"))throw new AssertionError(date);
 System.out.println("PASS: cloaked-space regression wraps into two bounded lines; date is 21 Oct 2026, 16:01");
 }
}
