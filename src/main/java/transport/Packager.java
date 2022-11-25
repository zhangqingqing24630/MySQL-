package transport;
import transport.Package;
//Packager 则是 Encoder 和 Transporter 的结合体，
//Encoder 负责把Package转为[Flag][data]的形式
//Transporter 负责将Package转为十六进制数据+"\n"，解码则按行读取即可，还负责发送和接收
public class Packager {
    private Transporter transpoter;
    private Encoder encoder;

    public Packager(Transporter transpoter, Encoder encoder) {
        this.transpoter = transpoter;
        this.encoder = encoder;
    }

    public void send(Package pkg) throws Exception {
        byte[] data = encoder.encode(pkg);
        transpoter.send(data);
    }

    public Package receive() throws Exception {
        byte[] data = transpoter.receive();
        return encoder.decode(data);
    }

    public void close() throws Exception {
        transpoter.close();
    }
}

