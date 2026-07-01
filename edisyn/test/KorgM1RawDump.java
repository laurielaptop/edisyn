package edisyn.test;
import edisyn.synth.korgm1.KorgM1Rec;
import java.io.*;

public class KorgM1RawDump {
    public static void main(String[] args) throws Exception {
        byte[] data = new FileInputStream("docs/KorgM1_piano16.syx").readAllBytes();
        byte[] raw = KorgM1Rec.convertTo8Bit(data, 5);
        System.out.println("Raw bytes (" + raw.length + "):");
        for (int i = 0; i < raw.length; i++) {
            System.out.printf("%3d: 0x%02X (%4d signed)  %c%n", i, raw[i]&0xFF,
                (int)(byte)raw[i], (raw[i]>=0x20 && raw[i]<=0x7E) ? (char)raw[i] : '.');
        }
    }
}
