package backend.vm;
import backend.dm.cache.AbstractCache;
import backend.dm.DataManager;
import backend.tm.TransactionManager;
import backend.tm.TransactionManagerImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import utils.Error;
import utils.Panic;

public class VersionManagerImpl extends AbstractCache<Entry> implements VersionManager {

    TransactionManager tm;
    DataManager dm;
    Map<Long, Transaction> activeTransaction;
    Lock lock;
    LockTable lt;

    public VersionManagerImpl(TransactionManager tm, DataManager dm) {
        super(0);
        this.tm = tm;
        this.dm = dm;
        this.activeTransaction = new HashMap<>();
        //key xid xid插入时还活跃着的事务合集
        activeTransaction.put(TransactionManagerImpl.SUPER_XID, Transaction.newTransaction(TransactionManagerImpl.SUPER_XID, 0, null));
        this.lock = new ReentrantLock();
        this.lt = new LockTable();
    }
//read() 方法读取一个 entry，注意判断下可见性即可
    //xid当前事务   uid要读取的事务
    @Override
    public byte[] read(long xid, long uid) throws Exception {
        lock.lock();
        //当前事务xid读取时的快照数据
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        if(t.err != null) {
            throw t.err;
        }

        Entry entry = null;
        try {
            //通过uid找要读取的事务dataItem
            entry = super.get(uid);
        } catch(Exception e) {
            if(e == Error.NullEntryException) {
                return null;
            } else {
                throw e;
            }
        }
        try {
            if(Visibility.isVisible(tm, t, entry)) {
                return entry.data();
            } else {
                return null;
            }
        } finally {
            entry.release();
        }
    }
//insert() 则是将数据包裹成 Entry，无脑交给 DM 插入即可：
    @Override
    public long insert(long xid, byte[] data) throws Exception {
        lock.lock();
        //xid插入时还活跃的快照
        Transaction t = activeTransaction.get(xid);
        lock.unlock();
        if(t.err != null) {
            throw t.err;
        }
        byte[] raw = Entry.wrapEntryRaw(xid, data);
        return dm.insert(xid, raw);
    }

    @Override
    public boolean delete(long xid, long uid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        if(t.err != null) {
            throw t.err;
        }
        Entry entry = null;
        try {
            entry = super.get(uid);
        } catch(Exception e) {
            if(e == Error.NullEntryException) {
                return false;
            } else {
                throw e;
            }
        }
        try {
            if(!Visibility.isVisible(tm, t, entry)) {
                return false;
            }
            Lock l = null;
            try {
                l = lt.add(xid, uid);
            } catch(Exception e) {
                //检测到死锁
                t.err = Error.ConcurrentUpdateException;
                internAbort(xid, true);
                t.autoAborted = true;
                throw t.err;
            }
            //没有死锁，但是需要等待当前事务拿到锁
            if(l != null) {
                l.lock();
                l.unlock();
            }
            if(entry.getXmax() == xid) {
                return false;
            }
            //System.out.println(entry.getXmax());
            //检查到版本跳跃，回滚事务
            if(Visibility.isVersionSkip(tm, t, entry)) {
                System.out.println("检查到版本跳跃，自动回滚");
                t.err = Error.ConcurrentUpdateException;
                internAbort(xid, true);
                t.autoAborted = true;
                throw t.err;
            }
            //删除事务，把当前xid设置为Xmax
            entry.setXmax(xid);
            return true;

        } finally {
            entry.release();
        }
    }
//begin() 每开启一个事务，并计算当前活跃的事务的结构，将其存放在 activeTransaction 中，
// 用于检查和快照使用：
    @Override
    public long begin(int level) {
        lock.lock();
        try {
            long xid = tm.begin();
            //activeTransaction 当前事务创建时活跃的事务,，如果level!=0,放入t的快照中
            Transaction t = Transaction.newTransaction(xid, level, activeTransaction);
            activeTransaction.put(xid, t);
            return xid;
        } finally {
            lock.unlock();
        }
    }
//commit() 方法提交一个事务，主要就是 free 掉相关的结构，并且释放持有的锁，并修改 TM 状态：
    @Override
    public void commit(long xid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        try {
            if(t.err != null) {
                throw t.err;
            }
        } catch(NullPointerException n) {
            Panic.panic(n);
        }

        lock.lock();
        activeTransaction.remove(xid);
        lock.unlock();

        lt.remove(xid);
        tm.commit(xid);
    }
//abort 事务的方法则有两种，手动和自动。手动指的是调用 abort() 方法，而自动，
// 则是在事务被检测出出现死锁时，会自动撤销回滚事务；
// 或者出现版本跳跃时，也会自动回滚：
    @Override
    public void abort(long xid) {
        internAbort(xid, false);
    }

    private void internAbort(long xid, boolean autoAborted) {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        //手动回滚
        if(!autoAborted) {
            activeTransaction.remove(xid);
        }
        lock.unlock();
        //自动回滚
        if(t.autoAborted) return;
        lt.remove(xid);
        tm.abort(xid);
    }

    public void releaseEntry(Entry entry) {
        //HashMap释放dataItem缓存
        super.release(entry.getUid());
    }
//由dataItem组成Entry
    @Override
    protected Entry getForCache(long uid) throws Exception {
        Entry entry = Entry.loadEntry(this, uid);
        if(entry == null) {
            throw Error.NullEntryException;
        }
        return entry;
    }

    @Override
    protected void releaseForCache(Entry entry) {
        entry.remove();
    }

}

