package com.portfolio.legacyexplainer.explain;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Heuristic detection of common legacy languages. Best-effort only:
 * the result is passed to the LLM as a hint and shown in the UI,
 * so a wrong guess is harmless.
 */
@Component
public class LanguageDetector {

    private static final Pattern COBOL = Pattern.compile(
            "(?im)^\\s*(IDENTIFICATION|DATA|PROCEDURE|ENVIRONMENT)\\s+DIVISION|\\bPIC\\s+[X9SV(]|\\bPERFORM\\b.*\\bUNTIL\\b"
                    + "|\\b(ADD|SUBTRACT|MULTIPLY|DIVIDE)\\b.*\\bGIVING\\b|\\bCOMPUTE\\s+\\w+(-\\w+)+\\s*=");
    private static final Pattern JAVA = Pattern.compile(
            "(?m)\\bpublic\\s+(final\\s+|abstract\\s+)?(class|interface|enum|record)\\b|^\\s*import\\s+java\\.|\\bSystem\\.out\\.print");
    private static final Pattern FORTRAN = Pattern.compile(
            "(?im)^\\s*(PROGRAM|SUBROUTINE|END\\s+DO|DIMENSION|IMPLICIT\\s+NONE)\\b|\\bWRITE\\s*\\(\\s*\\*\\s*,");
    private static final Pattern PASCAL = Pattern.compile(
            "(?im)^\\s*(program|procedure|function)\\s+\\w+.*;\\s*$|\\bbegin\\b[\\s\\S]*\\bend\\s*[.;]");
    private static final Pattern PLSQL = Pattern.compile(
            "(?im)\\bCREATE\\s+(OR\\s+REPLACE\\s+)?(PROCEDURE|FUNCTION|PACKAGE|TRIGGER)\\b|\\bDBMS_OUTPUT\\b|\\bEND\\s+LOOP\\s*;");
    private static final Pattern RPG = Pattern.compile(
            "(?im)^\\s*[HFDCP]\\s{1,10}\\w+.*\\b(DS|EVAL|CHAIN|SETLL)\\b|\\bdcl-(proc|pi|s|ds)\\b");
    private static final Pattern BASIC = Pattern.compile(
            "(?im)^\\s*\\d+\\s+(PRINT|GOTO|GOSUB|INPUT|REM|LET|IF)\\b");
    private static final Pattern C = Pattern.compile(
            "(?m)^\\s*#include\\s*[<\"]|\\bint\\s+main\\s*\\(|\\bprintf\\s*\\(|\\bmalloc\\s*\\(");
    private static final Pattern CPP = Pattern.compile(
            "(?m)^\\s*#include\\s*<(iostream|vector|string)>|\\bstd::|\\bcout\\s*<<|\\btemplate\\s*<");
    private static final Pattern ASSEMBLY = Pattern.compile(
            "(?im)^\\s*(section|segment)\\s+\\.|^\\s*(mov|jmp|push|pop|call)\\s+\\w+\\s*,?|^\\s*\\w+:\\s*$\\s*^\\s*(mov|lea|add)\\b");

    public String detect(String code) {
        if (COBOL.matcher(code).find()) return "COBOL";
        if (PLSQL.matcher(code).find()) return "PL/SQL";
        if (FORTRAN.matcher(code).find()) return "Fortran";
        if (RPG.matcher(code).find()) return "RPG";
        if (BASIC.matcher(code).find()) return "BASIC";
        if (CPP.matcher(code).find()) return "C++";
        if (JAVA.matcher(code).find()) return "Java";
        if (C.matcher(code).find()) return "C";
        if (PASCAL.matcher(code).find()) return "Pascal";
        if (ASSEMBLY.matcher(code).find()) return "Assembly";
        return "unknown";
    }
}
