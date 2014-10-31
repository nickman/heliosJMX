package com.heliosapm.jmx.expr;

import java.util.*;
import java.lang.*;
import java.util.regex.*;

class ExprTest {

        static final String[] testcases = new String[] {
        "'Tumblr' is an amazing app",
        "Tumblr is an amazing 'app'",
        "Tumblr is an 'amazing' app",
        "Tumblr is 'awesome' and 'amazing' ",
        "Tumblr's users' are disappointed ",
        "Tumblr's 'acquisition' complete but users' loyalty doubtful"
        };

    public static void main (String[] args) throws java.lang.Exception {
        Pattern p = Pattern.compile("(?:^|\\s)'([^']*?)'(?:$|\\s)", Pattern.MULTILINE);
        for (String arg : testcases) {
            System.out.print("Input: "+arg+" -> Matches: ");
            Matcher m = p.matcher(arg);
            if (m.find()) {
                System.out.print(m.group());
                while (m.find()) System.out.print(", "+m.group());
                System.out.println();
            } else {
                System.out.println("NONE");
            }
        } 
    }
}



//import java.util.regex.*;
//
//Pattern NAME_SEGMENT = Pattern.compile('^(.*?):(.*?)$');
//
//Pattern ATTRIBUTE_SPEC = Pattern.compile('^\\{attr\\[.*?\\]\\}$');
//Pattern DOMAIN = Pattern.compile("\\{domain\\}", Pattern.CASE_INSENSITIVE);
//Pattern KEY_SPEC = Pattern.compile('^\\{key\\[.*?\\]\\}$');
//
////Pattern SPLIT_SPEC = Pattern.compile("(?:^|\\s)\\{([^\\{]:)}(?:\$|\\s)", Pattern.MULTILINE);
////Pattern SPLIT_SPEC = Pattern.compile("(?=[^\\{]*(?:}))", Pattern.MULTILINE);
//Pattern SPLIT_SPEC = Pattern.compile(":(?:^\\{.*?:.*?})", Pattern.MULTILINE);
//
//Pattern QT_SPLIT_SPEC = Pattern.compile("(?:^|\\s)'([^']*?)'(?:\$|\\s)", Pattern.MULTILINE);
//
//nameSegments = [
//    "java.lang.gc:{key:type}{key:name}",
//    "   java.lang.gc   : {key:type}{key:name}  ",
//    "java.lang.gc.{attr:Foo}:{key:type}{key:name}",
//]
//
//samples = [
//        "'Tumblr' is an amazing app",
//        "Tumblr is an amazing 'app'",
//        "Tumblr is an 'amazing' app",
//        "Tumblr is 'awesome' and 'amazing' ",
//        "Tumblr's users' are disappointed ",
//        "Tumblr's 'acquisition' complete but users' loyalty doubtful"
//]
//
//
//samples.each() { 
//    println "SPLIT: ${QT_SPLIT_SPEC.split(it)}";
//}
//
//println "\n========================================================\n";
//
//nameSegments.each() { 
//    println "SPLIT: ${SPLIT_SPEC.split(it)}";
//}
//
//
///*
//nameSegments.each() {
//    println "Testing [$it]";
//    m = NAME_SEGMENT.matcher(it.replace(" ", "").replace("=", ""));
//    if(m.matches()) {
//        println "\tMatched: [${m.group(1)}] : [${m.group(2)}]";
//        
//    } else {
//        println "\tNO MATCH";
//    }
//}
//*/
//
//
//
//
//
//return null;
