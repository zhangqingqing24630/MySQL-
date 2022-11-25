package backend.tm;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class test {
    public static void main(String[] args) throws FileNotFoundException {
        TransactionManagerImpl tm=TransactionManager.create("cun/a");
        //开启一个事务
        long xid=tm.begin();
        //提交事务
        tm.commit(xid);
        //tm.abort(xid);//回滚事务
        System.out.println(tm.isAborted(xid));
    }
}
