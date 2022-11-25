package backend.vm;

import backend.dm.DataManager;
import backend.tm.TransactionManager;
//DM 层向上层提供了数据项（Data Item）的概念，VM 通过管理所有的数据项，
// 向上层提供了记录（Entry）的概念
//规定1：正在进行的事务，不会读取其他任何未提交的事务产生的数据。
//规定2：正在进行的事务，不会修改其他任何未提交的事务修改或产生的数据。

//虽然理论上，MVCC 实现了多版本，但是在实现中，VM 并没有提供 Update 操作，
// 对于字段的更新操作由后面的表和字段管理（TBM）实现。所以在 VM 的实现中，一条记录只有一个版本
public interface VersionManager {
    byte[] read(long xid, long uid) throws Exception;
    long insert(long xid, byte[] data) throws Exception;
    boolean delete(long xid, long uid) throws Exception;

    long begin(int level);
    void commit(long xid) throws Exception;
    void abort(long xid);
    public static VersionManager newVersionManager(TransactionManager tm, DataManager dm) {
        return new VersionManagerImpl(tm, dm);
    }

}
