package com.portfolio.legacyexplainer.explain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageDetectorTest {

    private final LanguageDetector detector = new LanguageDetector();

    @Test
    void detectsCobol() {
        String code = """
                IDENTIFICATION DIVISION.
                PROGRAM-ID. PAYROLL.
                DATA DIVISION.
                01 WS-TOTAL PIC 9(7)V99.
                """;
        assertThat(detector.detect(code)).isEqualTo("COBOL");
    }

    @Test
    void detectsCobolFromStatementsAlone() {
        // Diff excerpts often lack DIVISION headers and PIC clauses.
        String code = """
                ACCEPT WS-RATE
                SUBTRACT 40 FROM WS-HOURS GIVING WS-OVERTIME
                COMPUTE WS-GROSS = 40 * WS-RATE
                """;
        assertThat(detector.detect(code)).isEqualTo("COBOL");
    }

    @Test
    void detectsJava() {
        String code = """
                import java.util.List;
                public class LegacyBatch {
                    public static void main(String[] args) {
                        System.out.println("run");
                    }
                }
                """;
        assertThat(detector.detect(code)).isEqualTo("Java");
    }

    @Test
    void detectsFortran() {
        String code = """
                      PROGRAM MEAN
                      IMPLICIT NONE
                      WRITE(*,*) 'HELLO'
                      END
                """;
        assertThat(detector.detect(code)).isEqualTo("Fortran");
    }

    @Test
    void detectsC() {
        String code = """
                #include <stdio.h>
                int main(void) {
                    printf("hi\\n");
                    return 0;
                }
                """;
        assertThat(detector.detect(code)).isEqualTo("C");
    }

    @Test
    void returnsUnknownForUnrecognizedCode() {
        assertThat(detector.detect("lorem ipsum dolor sit amet")).isEqualTo("unknown");
    }
}
