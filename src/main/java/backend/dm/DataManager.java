package backend.dm;

import backend.dm.dataItem.DataItem;
import backend.dm.logger.Logger;
import backend.dm.page.PageOne;
import backend.dm.pageCache.PageCache;
import backend.tm.TransactionManager;


public interface DataManager {
    DataItem read(long uid) throws Exception;
    long insert(long xid, byte[] data) throws Exception;
    void close();
//从已有文件创建 DataManager 和从空文件创建 DataManager 的流程稍有不同，
// 除了 PageCache 和 Logger 的创建方式有所不同以外，
// 从空文件创建首先需要对第一页进行初始化，
// 而从已有文件创建，则是需要对第一页进行校验，来判断是否需要执行恢复流程。并重新对第一页生成随机字节

    public static DataManager create(String path, long mem, TransactionManager tm) {
        PageCache pc = PageCache.create(path, mem);
        Logger lg = Logger.create(path);

        DataManagerImpl dm = new DataManagerImpl(pc, lg, tm);
        //初始化PageOne
        dm.initPageOne();
        return dm;
    }
    public static DataManager open(String path, long mem, TransactionManager tm) {
        PageCache pc = PageCache.open(path, mem);
        Logger lg = Logger.open(path);
        DataManagerImpl dm = new DataManagerImpl(pc, lg, tm);
        // 校验PageOne
        //// 在打开已有文件时时读入PageOne，并验证正确性
        if(!dm.loadCheckPageOne()) {
            Recover.recover(tm, lg, pc);
        }
        //填入每一页的页号和该页的剩余空间
        dm.fillPageIndex();
        PageOne.setVcOpen(dm.pageOne);
        dm.pc.flushPage(dm.pageOne);
        return dm;
    }
}
