package org.jlab.paw;

import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.regex.*;

/** KUIP/Fortran-style numerical expressions translated to the restricted evaluator. */
final class NtupleExpressions {
    static ToDoubleFunction<double[]> compile(String expression,List<String> columns) {
        return Expression.compile(translate(expression,columns),columns);
    }
    static String translate(String expression,List<String>columns){
        String s=expression.strip();
        for(String op:List.of("AND","OR","NOT","EQ","NE","LT","LE","GT","GE")){
            String replacement=switch(op){case "AND"->"&&";case "OR"->"||";case "NOT"->"!";case "EQ"->"==";case "NE"->"!=";case "LT"->"<";case "LE"->"<=";case "GT"->">";default->">=";};
            s=s.replaceAll("(?i)\\."+op+"\\.",Matcher.quoteReplacement(replacement));
        }
        s=s.replaceAll("(?i)\\bALOG10(?=\\s*\\()","LOG10").replaceAll("(?i)\\bALOG(?=\\s*\\()","LOG").replaceAll("(?i)\\bD(SQRT|ABS|SIN|COS|EXP|LOG)(?=\\s*\\()","$1");
        s=s.replaceAll("(?i)(\\d[\\d.]*)D([+-]?\\d+)","$1e$2");
        s=s.replace("**","^").replaceAll("(?<![<>=!])=(?!=)","==");
        // PAW permits chained bounds, e.g. 0.4<X<0.8.
        Pattern bounds=Pattern.compile("([\\w.+-]+)\\s*([<>]=?)\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*([<>]=?)\\s*([\\w.+-]+)");
        Matcher b=bounds.matcher(s);StringBuffer expanded=new StringBuffer();while(b.find())b.appendReplacement(expanded,Matcher.quoteReplacement("("+b.group(1)+b.group(2)+b.group(3)+"&&"+b.group(3)+b.group(4)+b.group(5)+")"));b.appendTail(expanded);s=expanded.toString();
        for(String col:columns)s=s.replaceAll("(?i)(?<![\\w])"+Pattern.quote(col)+"(?![\\w])",Matcher.quoteReplacement(col));
        for(String fn:List.of("SQRT","ABS","SIN","COS","TAN","SINH","COSH","TANH","ASIN","ACOS","ATAN","ATAN2","SIGN","EXP","LOG","LOG10","FLOOR","INT","POW","MOD","MIN","MAX"))s=s.replaceAll("(?i)\\b"+fn+"(?=\\s*\\()",fn.toLowerCase(Locale.ROOT));
        s=s.replaceAll("(?i)\\bPI\\b","pi");
        return s;
    }
}
