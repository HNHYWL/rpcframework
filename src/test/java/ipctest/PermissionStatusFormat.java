package ipctest;
import static java.lang.System.*;

  enum PermissionStatusFormat {
    MODE(0, 16),
    GROUP(MODE.OFFSET + MODE.LENGTH, 25),
    USER(GROUP.OFFSET + GROUP.LENGTH, 23);

    final int OFFSET;
    final int LENGTH; //bit length
    final long MASK;

    PermissionStatusFormat(int offset, int length) {
      OFFSET = offset;
      LENGTH = length;
      MASK = ((-1L) >>> (64 - LENGTH)) << OFFSET;
    }

    long retrieve(long record) {
      return (record & MASK) >>> OFFSET;
    }

    long combine(long bits, long record) {
      return (record & ~MASK) | (bits << OFFSET);
    }
  }
  enum Z{
      MODE(0, 16),
      GROUP(MODE.OFFSET + MODE.LENGTH, 25),
      USER(GROUP.OFFSET + GROUP.LENGTH, 23);

      final int OFFSET;
      final int LENGTH; //bit length


      Z(int offset, int length) {
          OFFSET = offset;
          LENGTH = length;
      }

  }

enum Color {
      red("red", 10),
    green("green", 11),
    black("black", 12);
String name;
int age;

    Color(String name, int age) {
        this.name = name;
        this.age = age;
    }
}

class T {
    public static void main(String[] args) {
        System.out.println();
        TT black = TT.black;
        System.out.println(black);
        gc();
        out.println("opo0");

        switch (black) {
            case black:
            case red:
            case green:
            default:
        }

        System.out.println(black.name());
        System.out.println(black.ordinal());
        System.out.println(black.equals(TT.red));
        //
        float a=8;
        int b=Float.floatToIntBits(a);
        System.out.println(Integer.toBinaryString(b));
    }
}
  enum TT{
      red, green, black;
  }