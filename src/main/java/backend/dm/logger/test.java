package backend.dm.logger;

import backend.dm.pageCache.PageCache;
import backend.dm.pageCache.PageCacheImpl;

public class test {
    public static void main(String[] args) {
        Logger logger=Logger.create("log");
        byte b[]=new byte[128];
        logger.log(b);
        byte[] log=logger.next();
        logger.close();
    }
}
