package client;

import transport.Packager;
import transport.Package;
public class RoundTripper {
    //RoundTripper 类实际上实现了单次收发动作：
    private Packager packager;

    public RoundTripper(Packager packager) {
        this.packager = packager;
    }

    public Package roundTrip(Package pkg) throws Exception {
        packager.send(pkg);
        return packager.receive();
    }

    public void close() throws Exception {
        packager.close();
    }
}
