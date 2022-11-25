package backend.dm.pageCache;
import backend.dm.cache.AbstractCache;
import backend.dm.page.Page;
import backend.dm.page.PageImpl;
import utils.Panic;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import utils.Error;
//页面缓存的具体实现类，需要继承抽象缓存框架。实现 getForCache() 和 releaseForCache() 两个抽象方法
// getForCache() 直接从数据库中读取，并包裹成 Page 即可
public class PageCacheImpl extends AbstractCache<Page> implements PageCache {

    private static final int MEM_MIN_LIM = 10;
    public static final String DB_SUFFIX = ".db";

    private RandomAccessFile file;
    private FileChannel fc;
    private Lock fileLock;

    private AtomicInteger pageNumbers;

    PageCacheImpl(RandomAccessFile file, FileChannel fileChannel, int maxResource) {
        super(maxResource);
        if(maxResource < MEM_MIN_LIM) {//最少要求10页,给了16页
            Panic.panic(Error.MemTooSmallException);
        }
        long length = 0;
        try {
            length = file.length();
        } catch (IOException e) {
            Panic.panic(e);
        }
        this.file = file;
        this.fc = fileChannel;
        this.fileLock = new ReentrantLock();
        this.pageNumbers = new AtomicInteger((int)length / PAGE_SIZE);
    }


//PageCache 还使用了一个 AtomicInteger，
// 来记录了当前打开的数据库文件有多少页。这个数字在数据库文件被打开时就会被计算，并在新建页面时自增
//提一点，同一条数据是不允许跨页存储的，这一点会从后面的章节中体现。
// 这意味着，单条数据的大小不能超过数据库页面的大小
    //把数据写入以page包装的形式中，再写入数据库
    public int newPage(byte[] initData) {
        int pgno = pageNumbers.incrementAndGet();
        Page pg = new PageImpl(pgno, initData, null);
        flush(pg);
        return pgno;
    }
//根据页号从缓存中拿
    public Page getPage(int pgno) throws Exception {
        return get((long)pgno);
    }
//根据页号从数据库文件中拿，包裹成page
    @Override
    protected Page getForCache(long key) throws Exception {
        int pgno = (int)key;
        //根据页号算偏移量
        long offset = PageCacheImpl.pageOffset(pgno);
        ByteBuffer buf = ByteBuffer.allocate(PAGE_SIZE);
        fileLock.lock();
        try {
            fc.position(offset);
            fc.read(buf);
        } catch(IOException e) {
            Panic.panic(e);
        }
        fileLock.unlock();
        //this:PageCache的引用，用来方便在拿到 Page 的引用时
        //可以快速对这个页面的缓存进行释放操作。
        return new PageImpl(pgno, buf.array(), this);
    }

//而 releaseForCache() 驱逐页面时，也只需要根据页面是否是脏页面，来决定是否需要写回数据库
    @Override
    protected void releaseForCache(Page pg) {
        if(pg.isDirty()) {
            flush(pg);
            pg.setDirty(false);
        }
    }

    public void release(Page page) {
        release((long)page.getPageNumber());
    }

    public void flushPage(Page pg) {
        flush(pg);
    }
//把写好的pg格式数据写回数据库中
    private void flush(Page pg) {
        int pgno = pg.getPageNumber();
        long offset = pageOffset(pgno);//每一页初始位置的偏移量
        fileLock.lock();
        try {
            ByteBuffer buf = ByteBuffer.wrap(pg.getData());
            fc.position(offset);
            fc.write(buf);
            fc.force(false);
        } catch(IOException e) {
            Panic.panic(e);
        } finally {
            fileLock.unlock();
        }
    }

    public void truncateByBgno(int maxPgno) {
        long size = pageOffset(maxPgno + 1);
        try {
            file.setLength(size);
        } catch (IOException e) {
            Panic.panic(e);
        }
        pageNumbers.set(maxPgno);
    }

    @Override
    public void close() {
        super.close();
        try {
            fc.close();
            file.close();
        } catch (IOException e) {
            Panic.panic(e);
        }
    }

    public int getPageNumber() {
        return pageNumbers.intValue();
    }
// 页号从 1 开始
    private static long pageOffset(int pgno) {
        return (pgno-1) * PAGE_SIZE;
    }

}

