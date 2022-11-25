package backend.vm;
import backend.tm.TransactionManagerImpl;

import java.util.HashMap;
import java.util.Map;


// vm对一个事务的抽象
public class Transaction {
    public long xid;
    public int level;
    public Map<Long, Boolean> snapshot;
    public Exception err;
    public boolean autoAborted;
//需要提供一个结构，来抽象一个事务，以保存active快照数据：
    //事务id  隔离级别  快照
    public static Transaction newTransaction(long xid, int level, Map<Long, Transaction> active) {
        Transaction t = new Transaction();
        t.xid = xid;
        t.level = level;
        if(level != 0) {//隔离级别为可重复读,读已提交不需要快照信息
            t.snapshot = new HashMap<>();
            for(Long x : active.keySet()) {
                t.snapshot.put(x, true);
            }
        }
        return t;
    }
    public boolean isInSnapshot(long xid) {
        if(xid == TransactionManagerImpl.SUPER_XID) {
            return false;
        }
        return snapshot.containsKey(xid);
    }
}

