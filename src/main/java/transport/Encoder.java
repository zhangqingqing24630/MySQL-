package transport;
import utils.Error;
import com.google.common.primitives.Bytes;

import java.util.Arrays;
//将SQL语句和错误数据一起打包成一个Package，
// 然后packager调用Transporter将这个Package通过socket连接进行发送和接收。
// encoder的编解码主要就是对sql语句里面的异常进行封包和解包处理工作。
// Package规则如下：
//[Flag][data]
//若 flag 为 0，表示发送的是数据，那么 data 即为这份数据本身；
// 如果 flag 为 1，表示发送的是错误，data 是 Exception.getMessage() 的错误提示信息
public class Encoder {
    public byte[] encode(Package pkg) {
        if(pkg.getErr() != null) {
            Exception err = pkg.getErr();
            String msg = "Intern server error!";
            if(err.getMessage() != null) {
                msg = err.getMessage();
            }
            //[1][err]
            return Bytes.concat(new byte[]{1}, msg.getBytes());
        } else {
            //[0][data]
            return Bytes.concat(new byte[]{0}, pkg.getData());
        }
    }

    public Package decode(byte[] data) throws Exception {
        if(data.length < 1) {
            throw Error.InvalidPkgDataException;
        }
        if(data[0] == 0) {
            return new Package(Arrays.copyOfRange(data, 1, data.length), null);
        } else if(data[0] == 1) {
            return new Package(null, new RuntimeException(new String(Arrays.copyOfRange(data, 1, data.length))));
        } else {
            throw Error.InvalidPkgDataException;
        }
    }

}
