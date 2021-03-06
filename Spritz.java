import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;

public class Spritz {

  private static final int SPRITZ_N=256;

  private int i;
  private int j;
  private int k;
  private int z;
  private int a;
  private int w;

  private int[] S = new int[SPRITZ_N];

  private static enum ProgramMode {
    ENCRYPT,
    DECRYPT
  };

  private static final int IV_SIZE=10;

  public Spritz() {

    i = 0;
    j = 0;
    k = 0;
    z = 0;
    a = 0;
    w = 1;
  
    for(int v=0; v < SPRITZ_N; v++) {
      S[v] = v;
    }
    
  }

  private int low(byte b) {
    return (b & 0x0F);
  }

  private int high(byte b) {
    return ((b & 0xFF) >>> 4);
  }

  private void swap(int a, int b) {
    int tmp = S[a];
    S[a] = S[b];
    S[b] = tmp;
  }

  public static final int gcd(int x1,int x2) {
    //From http://www.java2s.com/Tutorial/Java/0120__Development/GreatestCommonDivisorGCDofpositiveintegernumbers.htm
    if(x1<0 || x2<0) {
      throw new IllegalArgumentException("Cannot compute the GCD if one integer is negative.");
    }

    int a1,b1,g1,z1;

    if(x1>x2) {
      a1 = x1;
      b1 = x2;
    } else {
      a1 = x2;
      b1 = x1;
    }

    if(b1==0) return 0;

    g1 = b1;
    while (g1!=0) {
      z1= a1%g1;
      a1 = g1;
      g1 = z1;
    }
    return a1;
  }

  private void update() {
    i = ((byte) (i + w)) & 0xff;
    j = ((byte) (((byte) (k + S[(byte)(j + S[i]) & 0xff]) & 0xff))) & 0xff;
    k = ((byte) (i + k + S[j])) & 0xff;
    swap(i, j);
  }

  private void whip(int r) {
    for(int v = 0; v < r; v++) {
      update();
    }

    do {
      w = ((byte) (w + 1)) & 0xff;
    } while(gcd(w, SPRITZ_N) != 1);

  }

  private void crush() {
    for(int v=0; v < (SPRITZ_N / 2); v++) {
      if((S[v]) > S[SPRITZ_N - 1 - v]) {
        swap(v, SPRITZ_N - 1 - v);
      }
    }
  }

  private void shuffle() {
    whip(SPRITZ_N * 2);
    crush();
    whip(SPRITZ_N * 2);
    crush();
    whip(SPRITZ_N * 2);
    a = 0;
  }

  private void absorb_nibble(int x) {
    if(a == (SPRITZ_N/2)) {
      shuffle();
    }

    swap(a, (byte) ((SPRITZ_N/2) + x) & 0xff);
    
    a = ((byte) (a + 1)) & 0xff;
  }

  private void absorb_byte(byte b) {
    absorb_nibble(low(b));
    absorb_nibble(high(b));
  }

  private void absorb(byte[] I) {
    for(int v=0; v < I.length; v++) {
      absorb_byte(I[v]);
    }
  }
 
  private void absorb_stop() {
    if(a == SPRITZ_N/2) {
      shuffle();
    }

    a = ((byte) (a + 1)) & 0xff;
  }

  private byte drip() {
    if(a != 0) {
      shuffle();
    }

    update();

    z = S[(byte)(j + S[(byte)(i + S[(byte)(z + k) & 0xff]) & 0xff]) & 0xff];

    return (byte) z;
  }

  public static void print_usage() {
    System.out.println("Usage:\n" +
           "  spritz [mode] [key] [input file] [output file]\n" +
           "\n" + 
           "Options: \n" +
           "  mode: encrypt / decrypt\n" + 
           "  key: encryption key, may be up to 245 bytes long\n" +
           "  input file: the input to read\n" + 
           "  output file: the input to write");

  }

  public static void main(String[] args) {

    if(args.length < 3) {
      print_usage();
      System.exit(0);
    }
   
    ProgramMode program_action = null;
    byte[] key;
    byte[] iv = new byte[IV_SIZE];
    Spritz spritz;
    InputStream in = null;
    OutputStream out = null;
    int c=0;

    if(args[0].equals("encrypt")) {
      program_action = ProgramMode.ENCRYPT;
    } else if(args[0].equals("decrypt")) {
      program_action = ProgramMode.DECRYPT;
    } else {
      System.out.println("Unknown mode " + args[0] + ".");
      print_usage();
      System.exit(0);
    }

    key = args[1].getBytes();

    if(args.length >= 3) {
      try {
        in = new FileInputStream(args[2]);
      } catch(Exception ex) {
        System.out.println("Input file " + args[2] + " not found.");
        System.exit(0);
      }
    }

    if(args.length >= 4) {
      try {
        out = new FileOutputStream(args[3]);
      } catch(Exception ex) {
        System.out.println("Output file " + args[3] + " not found.");
        System.exit(0);
      }
    }

    if(program_action == ProgramMode.ENCRYPT) {
      SecureRandom random = new SecureRandom();
      random.nextBytes(iv);
      try { 
        out.write(iv); 
      } catch(Exception ex) {
        ex.printStackTrace(System.out);
        System.exit(0);
      }
    } else if (program_action == ProgramMode.DECRYPT) {
      int r = 0;
      try {
        r = in.read(iv);
      } catch(Exception ex) {
        ex.printStackTrace(System.out);
        System.exit(0);
      }
      if(r != IV_SIZE) {
        System.out.println("Could not read initialisation vector.");
        System.exit(0);
      }
    }

    spritz = new Spritz();
    spritz.absorb(key);
    spritz.absorb_stop();
    spritz.absorb(iv);

    try {

      c = in.read();

      while(c != -1) {
        byte r=0;
  
        if(program_action == ProgramMode.ENCRYPT) {
          r = (byte)(c + (spritz.drip() & 0xff));
        } else if (program_action == ProgramMode.DECRYPT) {
          r = (byte)(c - (spritz.drip() & 0xff));
        }
  
        out.write(r);
        c = in.read();
  
      }

      in.close();
      out.close();
    } catch(Exception ex) {
      ex.printStackTrace(System.out);
      System.exit(0);
    }

    System.exit(0);
  }

}
