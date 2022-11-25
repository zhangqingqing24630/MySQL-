package backend.dm;

import backend.dm.DataManager;
import backend.dm.dataItem.DataItem;
import backend.dm.dataItem.DataItemImpl;
import backend.dm.logger.Logger;
import backend.dm.pageCache.PageCache;
import backend.dm.pageCache.PageCacheImpl;
import backend.tm.TransactionManager;
import backend.tm.TransactionManagerImpl;

public class test {
    public static void main(String[] args) throws Exception {
        TransactionManagerImpl tm=TransactionManager.open("cun/tm");
        PageCacheImpl pc=PageCache.open("cun/page",1 << 17);
        Logger lg = Logger.open("cun/logger");
        //DataManager dm= DataManager.open("cun/page", 1 << 17, tm);
        Recover.recover(tm, lg, pc);
    }
}
