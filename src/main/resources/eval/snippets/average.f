      PROGRAM AVG
      REAL X(100), S, M
      INTEGER N, I
      READ (*,*) N
      S = 0.0
      DO 10 I = 1, N
         READ (*,*) X(I)
         S = S + X(I)
   10 CONTINUE
      M = S / REAL(N)
      WRITE (*,*) 'MEAN = ', M
      END
