package backend.dm.logger;
import com.google.common.primitives.Bytes;
import utils.Panic;
import utils.Error;
import utils.Parser;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 日志文件读写
 *
 * 日志文件标准格式为：
 * [XChecksum] [Log1] [Log2] ... [LogN] [BadTail]
 * XChecksum 是一个四字节的整数，是对后续所有日志计算的校验和
 *Log1 ~ LogN 是常规的日志数据，
 * BadTail 是在数据库崩溃时，没有来得及写完的日志数据，这个 BadTail 不一定存在。
 * 每条正确日志的格式为：
 * [Size] [Checksum] [Data]
 * Size 4字节int 标识Data长度
 * Checksum  则是该条日志的校验和
 */
public class LoggerImpl implements Logger {

    private static final int SEED = 13331;

    private static final int OF_SIZE = 0;
    private static final int OF_CHECKSUM = OF_SIZE + 4;
    private static final int OF_DATA = OF_CHECKSUM + 4;

    public static final String LOG_SUFFIX = ".log";

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock lock;

    private long position;  // 当前日志指针的位置
    private long fileSize;  // 初始化时记录，log操作不更新
    private int xChecksum;

    LoggerImpl(RandomAccessFile raf, FileChannel fc) {
        this.file = raf;
        this.fc = fc;
        lock = new ReentrantLock();
    }

    LoggerImpl(RandomAccessFile raf, FileChannel fc, int xChecksum) {
        this.file = raf;
        this.fc = fc;
        this.xChecksum = xChecksum;
        lock = new ReentrantLock();
    }

    void init() {
        long size = 0;
        try {
            size = file.length();
        } catch (IOException e) {
            Panic.panic(e);
        }
        if(size < 4) {
            Panic.panic(Error.BadLogFileException);
        }
        ByteBuffer raw = ByteBuffer.allocate(4);
        try {
            fc.position(0);
            fc.read(raw);
        } catch (IOException e) {
            Panic.panic(e);
        }
        int xChecksum = Parser.parseInt(raw.array());
        //XChecksum 是一个四字节的整数，是对后续所有日志计算的校验和
        this.fileSize = size;
        this.xChecksum = xChecksum;
        checkAndRemoveTail();
    }

    // 在打开一个日志文件时，需要首先校验日志文件的 XChecksum，并移除文件尾部可能存在的 BadTail，
    // 由于 BadTail 该条日志尚未写入完成，文件的校验和也就不会包含该日志的校验和，
    // 去掉 BadTail 即可保证日志文件的一致性。
    private void checkAndRemoveTail() {
        rewind();//position=4
        int xCheck = 0;
        while(true) {
            //遍历日志
            byte[] log = internNext();
            if(log == null) break;
            xCheck = calChecksum(xCheck, log);
        }
        //xCheck 多条日志总的校验和
        if(xCheck != xChecksum) {
            Panic.panic(Error.BadLogFileException);
        }
        try {
            truncate(position);
        } catch (Exception e) {
            Panic.panic(e);
        }
        try {
            file.seek(position);
        } catch (IOException e) {
            Panic.panic(e);
        }
        rewind();
    }
//对log数组里面的所有日志求校验和
    private int calChecksum(int xCheck, byte[] log) {
        for (byte b : log) {
            xCheck = xCheck * SEED + b;
        }
        return xCheck;
    }

//向日志文件写入日志时，也是首先将数据包裹成日志格式，
// 写入文件后，再更新文件的校验和，更新校验和时，会刷新缓冲区，保证内容写入磁盘。
    @Override
    public void log(byte[] data) {
        byte[] log = wrapLog(data);
        ByteBuffer buf = ByteBuffer.wrap(log);
        lock.lock();

        try {
            fc.position(fc.size());
            fc.write(buf);
        } catch(IOException e) {
            Panic.panic(e);
        } finally {
            lock.unlock();
        }
        updateXChecksum(log);
    }
//向日志文件写入日志时，也是首先将数据包裹成日志格式，
// 写入文件后，再更新文件的校验和，更新校验和时，会刷新缓冲区，保证内容写入磁盘。
    private void updateXChecksum(byte[] log) {
        this.xChecksum = calChecksum(this.xChecksum, log);
        try {
            fc.position(0);
            fc.write(ByteBuffer.wrap(Parser.int2Byte(xChecksum)));
            fc.force(false);
        } catch(IOException e) {
            Panic.panic(e);
        }
    }

    private byte[] wrapLog(byte[] data) {
        //把数据部分打包成log格式
        byte[] checksum = Parser.int2Byte(calChecksum(0, data));
        byte[] size = Parser.int2Byte(data.length);
        return Bytes.concat(size, checksum, data);
    }

    @Override
    public void truncate(long x) throws Exception {
        lock.lock();
        try {
            fc.truncate(x);
        } finally {
            lock.unlock();
        }
    }
//通过 next() 方法，不断地从文件中读取下一条日志，并将日志文件的 Data 解析出来并返回。
//其中 position 是当前日志文件读到的位置偏移：
    private byte[] internNext() {
        //[XChecksum] [Log1] [Log2]
        //[Size] [Checksum] [Data]
        if(position + OF_DATA >= fileSize) {//size>fileSize
            return null;
        }
        // 读取单条日志的size（data段的字节数）
        ByteBuffer tmp = ByteBuffer.allocate(4);
        try {
            fc.position(position);
            fc.read(tmp);
        } catch(IOException e) {
            Panic.panic(e);
        }
        int size = Parser.parseInt(tmp.array());
        if(position + size + OF_DATA > fileSize) {
            return null;
        }
        // 读取checksum+data
        ByteBuffer buf = ByteBuffer.allocate(OF_DATA + size);
        try {
            fc.position(position);
            fc.read(buf);
        } catch(IOException e) {
            Panic.panic(e);
        }
        //单条日志的checksum+data
        byte[] log = buf.array();
        // 一条日志根据存放的数据算出来的校验和
        int checkSum1 = calChecksum(0, Arrays.copyOfRange(log, OF_DATA, log.length));
        //一条日志的Checksum部分存的东西
        int checkSum2 = Parser.parseInt(Arrays.copyOfRange(log, OF_CHECKSUM, OF_DATA));
        if(checkSum1 != checkSum2) {//毁坏的日志文件
            return null;
        }
        position += log.length;
        return log;
    }
//返回下一条日志的数据
    @Override
    public byte[] next() {
        lock.lock();
        try {
            byte[] log = internNext();
            if(log == null) return null;
            return Arrays.copyOfRange(log, OF_DATA, log.length);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void rewind() {
        position = 4;
    }

    @Override
    public void close() {
        try {
            fc.close();
            file.close();
        } catch(IOException e) {
            Panic.panic(e);
        }
    }

}

