package backend.dm.dataItem;

import backend.dm.cache.SubArray;
import backend.dm.DataManagerImpl;
import backend.dm.page.Page;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *
 *是 DM 层向上层提供的数据抽象。上层模块通过地址，向 DM 请求到对应的 DataItem，再获取到其中的数据
 * dataItem 结构如下：
 * [ValidFlag] [DataSize] [Data]
 * ValidFlag 1字节，0为合法，1为非法，删除一个 DataItem，只需要简单地将其有效位设置为 0
 * DataSize  2字节，标识Data的长度
 * 删除一个 DataItem，只需要简单地将其有效位设置为 0
 */
public class DataItemImpl implements DataItem {

    static final int OF_VALID = 0;
    static final int OF_SIZE = 1;//size占两个字节
    static final int OF_DATA = 3;//data在第3个字节之后

    private SubArray raw;
    private byte[] oldRaw;
    private Lock rLock;
    private Lock wLock;
    //保存一个 dm 的引用是因为其释放依赖 dm 的释放（dm 同时实现了缓存接口，用于缓存 DataItem），
    // 以及修改数据时落日志。
    private DataManagerImpl dm;
    private long uid;//uid由页号和offset组成
    private Page pg;

    public DataItemImpl(SubArray raw, byte[] oldRaw, Page pg, long uid, DataManagerImpl dm) {
        this.raw = raw;
        this.oldRaw = oldRaw;
        ReadWriteLock lock = new ReentrantReadWriteLock();
        rLock = lock.readLock();
        wLock = lock.writeLock();
        this.dm = dm;
        this.uid = uid;
        this.pg = pg;
    }

    public boolean isValid() {
        //[ValidFlag]
        return raw.raw[raw.start+OF_VALID] == (byte)0;
    }
//上层模块在获取到 DataItem 后，可以通过 data() 方法，
//返回dataItemImpl的data部分
// 该方法返回的数组是数据共享的，而不是拷贝实现的，所以使用了 SubArray。
    @Override
    public SubArray data() {
        //[Data]
        return new SubArray(raw.raw, raw.start+OF_DATA, raw.end);//3
    }
//修改dataitem的步骤
//在修改之前需要调用 before() 方法，
//把raw移到oldRaw里面
    @Override
    public void before() {
        wLock.lock();
        pg.setDirty(true);
        System.arraycopy(raw.raw, raw.start, oldRaw, 0, oldRaw.length);
    }
// 想要撤销修改时，调用 unBefore() 方法，oldraw移到raw里面
    @Override
    public void unBefore() {
        System.arraycopy(oldRaw, 0, raw.raw, raw.start, oldRaw.length);
        wLock.unlock();
    }
//在修改完成后，调用after() 方法，主要就是调用 dm 中的一个方法，对修改操作落日志，不赘述。
    @Override
    public void after(long xid) {
        dm.logDataItem(xid, this);
        wLock.unlock();
    }

    @Override
    public void release() {
        dm.releaseDataItem(this);
    }

    @Override
    public void lock() {
        wLock.lock();
    }

    @Override
    public void unlock() {
        wLock.unlock();
    }

    @Override
    public void rLock() {
        rLock.lock();
    }

    @Override
    public void rUnLock() {
        rLock.unlock();
    }

    @Override
    public Page page() {
        return pg;
    }

    @Override
    public long getUid() {
        return uid;
    }

    @Override
    public byte[] getOldRaw() {
        return oldRaw;
    }

    @Override
    public SubArray getRaw() {
        return raw;
    }

}
