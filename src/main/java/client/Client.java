package client;

import transport.Packager;
import transport.Package;
public class Client {
    private RoundTripper rt;

    public Client(Packager packager) {
        this.rt = new RoundTripper(packager);
    }
//客户端有一个简单的 Shell，接收 shell 发过来的sql语句，
// 并打包成pkg进行单次收发操作roundTrip()，得到执行结果并返回
    public byte[] execute(byte[] stat) throws Exception {
        Package pkg = new Package(stat, null);
        //RoundTripper 类实际上实现了单次收发动作：
        Package resPkg = rt.roundTrip(pkg);
        if(resPkg.getErr() != null) {
            throw resPkg.getErr();
        }
        return resPkg.getData();
    }

    public void close() {
        try {
            rt.close();
        } catch (Exception e) {
        }
    }

}

