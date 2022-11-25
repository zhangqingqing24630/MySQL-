package backend.dm;
//DM 直接管理数据库 DB 文件和日志文件。
// DM 的主要职责有：
// 1) 分页管理 DB 文件，并进行缓存；
// 2) 管理日志文件，保证在发生错误时可以根据日志进行恢复；
// 3) 抽象 DB 文件为 DataItem 供上层模块使用，并提供缓存。

import backend.dm.cache.AbstractCache;
import backend.dm.dataItem.DataItem;
import backend.dm.dataItem.DataItemImpl;
import backend.dm.logger.Logger;
import backend.dm.page.Page;
import backend.dm.page.PageOne;
import backend.dm.page.PageX;
import backend.dm.pageCache.PageCache;
import backend.dm.pageIndex.PageIndex;
import backend.dm.pageIndex.PageInfo;
import backend.tm.TransactionManager;
import utils.Panic;
import utils.Error;
import utils.Types;
//DM 的主要职责有：
// 1) 分页管理 DB 文件，并进行缓存；
// 2) 管理日志文件，保证在发生错误时可以根据日志进行恢复；
// 3) 抽象 DB 文件为 DataItem 供上层模块使用，并提供缓存。
//总结
//上层模块和文件系统之间的一个抽象层，向下直接读写文件，向上提供数据的包装；另外就是日志功能。

//可以注意到，无论是向上还是向下，DM 都提供了一个缓存的功能，用内存操作来保证效率。

//由于分页管理和数据项（DataItem）管理都涉及缓存，这里设计一个更通用的缓存框架，引用计数缓存框架

//LRU 策略中，资源驱逐不可控，上层模块无法感知。
// 而引用计数策略正好解决了这个问题，只有上层模块主动释放引用，
// 缓存在确保没有模块在使用这个资源了，才会去驱逐资源。
public class DataManagerImpl extends AbstractCache<DataItem> implements DataManager {

    TransactionManager tm;
    PageCache pc;
    Logger logger;
    PageIndex pIndex;
    Page pageOne;

    public DataManagerImpl(PageCache pc, Logger logger, TransactionManager tm) {
        super(0);
        this.pc = pc;
        this.logger = logger;
        this.tm = tm;
        this.pIndex = new PageIndex();
    }
//DM 层提供了三个功能供上层使用，分别是读、插入和修改。
// 修改是通过读出的 DataItem 实现的，于是 DataManager 只需要提供 read() 和 insert() 方法。
//read() 根据 UID 从缓存中获取 DataItem，并校验有效位：
    @Override
    public DataItem read(long uid) throws Exception {
        //以PageX管理页面的时候FSO后面的DATA其实就是一个个的DataItem包
        DataItemImpl di = (DataItemImpl)super.get(uid);
        //[ValidFlag]是否为0
        if(!di.isValid()) {
            di.release();
            return null;
        }
        return di;
    }

//insert() 方法，在 pageIndex 中获取一个足以存储插入内容的页面的页号，获取页面后，
// 首先需要写入插入日志，接着才可以通过 pageX 插入数据，并返回插入位置的偏移。
// 最后需要将页面信息重新插入 pageIndex。
    @Override
    public long insert(long xid, byte[] data) throws Exception {
        //page里的data是dataItem
        byte[] raw = DataItem.wrapDataItemRaw(data);//打包成dateItem的格式
        if(raw.length > PageX.MAX_FREE_SPACE) {
            throw Error.DataTooLargeException;
        }
        // 尝试获取可用页
        PageInfo pi = null;
        for(int i = 0; i < 5; i ++) {
            pi = pIndex.select(raw.length);
            if (pi != null) {
                break;
            } else {
                //没有满足条件的数据页，新建一个数据页并写入数据库文件
                int newPgno = pc.newPage(PageX.initRaw());
                pIndex.add(newPgno, PageX.MAX_FREE_SPACE);
            }
        }
        if(pi == null) {
            throw Error.DatabaseBusyException;
        }
        //System.out.println(pi.pgno);
        Page pg = null;
        int freeSpace = 0;
        try {
            pg = pc.getPage(pi.pgno);
            // 首先做日志  raw dataItem page里的data
            byte[] log = Recover.insertLog(xid, pg, raw);
            logger.log(log);
            // 再执行插入操作
            short offset = PageX.insert(pg, raw);
            pg.release();
            //返回插入位置的偏移
            return Types.addressToUid(pi.pgno, offset);
        } finally {
            // 将取出的pg重新插入pIndex
            if(pg != null) {
                pIndex.add(pi.pgno, PageX.getFreeSpace(pg));
            } else {
                pIndex.add(pi.pgno, freeSpace);
            }
        }
    }
//DataManager 正常关闭时，需要执行缓存和日志的关闭流程，不要忘了设置第一页的字节校验：
    @Override
    public void close() {
        super.close();
        logger.close();

        PageOne.setVcClose(pageOne);
        pageOne.release();
        pc.close();
    }

    // 为xid生成update日志
    public void logDataItem(long xid, DataItem di) {
        byte[] log = Recover.updateLog(xid, di);
        logger.log(log);
    }

    public void releaseDataItem(DataItem di) {
        super.release(di.getUid());
    }
//DataItem缓存中无dataItem，从PageCache缓存中找。
// 从 pageCache 中获取到页面，再根据偏移，解析出 DataItem 即可：
    @Override
    protected DataItem getForCache(long uid) throws Exception {
        short offset = (short)(uid & ((1L << 16) - 1));
        uid >>>= 32;
        //从 key 中解析出页号
        int pgno = (int)(uid & ((1L << 32) - 1));
        //从 pageCache 中获取到页面
        Page pg = pc.getPage(pgno);
        return DataItem.parseDataItem(pg, offset, this);
    }
//DataItem 缓存释放，需要将 DataItem 写回数据源，由于对文件的读写是以页为单位进行的，
// 只需要将 DataItem 所在的页 release 即可
    @Override
    protected void releaseForCache(DataItem di) {
        di.page().release();
    }
    // 在创建文件时初始化PageOne
    void initPageOne() {
        int pgno = pc.newPage(PageOne.InitRaw());
        assert pgno == 1;
        try {
            pageOne = pc.getPage(pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }
        pc.flushPage(pageOne);
    }
    // 校验PageOne
    boolean loadCheckPageOne() {
        try {
            pageOne = pc.getPage(1);
        } catch (Exception e) {
            Panic.panic(e);
        }
        return PageOne.checkVc(pageOne);
    }

    // 初始化，在 DataManager 被创建时，需要获取所有页面并填充 PageIndex
    //填入每一页的页号和该页的剩余空间
    void fillPageIndex() {
        int pageNumber = pc.getPageNumber();
        for(int i = 2; i <= pageNumber; i ++) {
            Page pg = null;
            try {
                pg = pc.getPage(i);
            } catch (Exception e) {
                Panic.panic(e);
            }
            pIndex.add(pg.getPageNumber(), PageX.getFreeSpace(pg));
            pg.release();
        }
    }
}

