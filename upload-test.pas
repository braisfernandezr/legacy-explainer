PROGRAM InterestCalc;
{ Computes compound interest on customer accounts.  Written 1989. }

CONST
  MaxAccts = 50;

TYPE
  Acct = RECORD
    Id      : Integer;
    Balance : Real;
    Rate    : Real;
  END;

VAR
  Accounts : ARRAY[1..MaxAccts] OF Acct;
  N, I, M  : Integer;

FUNCTION Grow(B, R : Real; Months : Integer) : Real;
VAR
  K : Integer;
BEGIN
  FOR K := 1 TO Months DO
    B := B + B * R / 12.0;
  Grow := B
END;

BEGIN
  Write('How many accounts? ');
  ReadLn(N);
  FOR I := 1 TO N DO
  BEGIN
    Accounts[I].Id := 1000 + I;
    Accounts[I].Balance := 250.0 * I;
    Accounts[I].Rate := 0.05
  END;

  Write('Months to project? ');
  ReadLn(M);

  FOR I := 1 TO N DO
    WriteLn(Accounts[I].Id, ': ',
            Grow(Accounts[I].Balance, Accounts[I].Rate, M) : 10 : 2)
END.
