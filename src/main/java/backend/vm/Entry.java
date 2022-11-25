package backend.vm;
import java.util.Arrays;

import backend.dm.cache.SubArray;
import backend.dm.dataItem.DataItem;
import com.google.common.primitives.Bytes;
import utils.Parser;

/**
 * VM向上层抽象出entry
 * VM 并没有提供 Update 操作，对于字段的更新操作由后面的表和字段管理（TBM）实现。
 *所以在 VM 的实现中，一条记录只有一个版本
 * entry结构：
 * [XMIN] [XMAX] [data]
 *  XMIN 应当在版本创建时填写，
 *  而 XMAX 则在版本被删除，或者有新版本出现时填写。
 *
 *  当想删除一个版本时，只需要设置其 XMAX，
 *  这样，这个版本对每一个 XMAX 之后的事务都是不可见的，也就等价于删除了
 *
 *  更新操作由后面的表和字段管理（TBM）实现
 *  DATA 就是这条记录持有的数据
 */
public class Entry {

    private static final int OF_XMIN = 0;
    private static final int OF_XMAX = OF_XMIN+8;
    private static final int OF_DATA = OF_XMAX+8;
    private long uid;

    //一条记录[data]存储在一条 Data Item 中，所以 Entry 中保存一个 DataItem 的引用即可：
    private DataItem dataItem;
    private VersionManager vm;

    public static Entry newEntry(VersionManager vm, DataItem dataItem, long uid) {
        Entry entry = new Entry();
        entry.uid = uid;//页号和offset
        entry.dataItem = dataItem;//page里面取出需要用到的包装的数据
        entry.vm = vm;
        return entry;
    }
//vm找dataManager，找DataItem
    public static Entry loadEntry(VersionManager vm, long uid) throws Exception {
        DataItem di = ((VersionManagerImpl)vm).dm.read(uid);
        return newEntry(vm, di, uid);
    }


    //创建记录，一条 Entry 中存储的数据格式如下
    //XMIN 是创建该条记录（版本）的事务编号，而 XMAX 则是删除该条记录（版本）的事务编号。
    // 它们的作用将在下一节中说明。DATA 就是这条记录持有的数据
    public static byte[] wrapEntryRaw(long xid, byte[] data) {
        byte[] xmin = Parser.long2Byte(xid);
        byte[] xmax = new byte[8];
        return Bytes.concat(xmin, xmax, data);
    }

    public void release() {
        ((VersionManagerImpl)vm).releaseEntry(this);
    }

    public void remove() {
        dataItem.release();
    }

    //把dataItem的data部分的data拷贝出来
    //这里以拷贝的形式返回数据，如果需要修改的话，需要对 DataItem 执行 before() 方法
    public byte[] data() {
        dataItem.rLock();
        try {
            //dataItem的data部分是Xmin|Xmax|data
            SubArray sa = dataItem.data();
            byte[] data = new byte[sa.end - sa.start - OF_DATA];
            System.arraycopy(sa.raw, sa.start+OF_DATA, data, 0, data.length);
            return data;
        } finally {
            dataItem.rUnLock();
        }
    }

    public long getXmin() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start+OF_XMIN, sa.start+OF_XMAX));
        } finally {
            dataItem.rUnLock();
        }
    }

    public long getXmax() {
        dataItem.rLock();
        try {
            SubArray sa = dataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start+OF_XMAX, sa.start+OF_DATA));
        } finally {
            dataItem.rUnLock();
        }
    }

//修改max
// 需要对 DataItem 执行 before() 方法
    //修改数据（先删除后加入）只是加入xmax,并另外插入一条dataItem，实际的data部分长度不变
    public void setXmax(long xid) {
        dataItem.before();//把raw复制到oldRaw里面
        try {
            SubArray sa = dataItem.data();
            //给raw更换一个事务更新或者结束的xid
            System.arraycopy(Parser.long2Byte(xid), 0, sa.raw, sa.start+OF_XMAX, 8);
        } finally {
            //对操作写更新日志
            dataItem.after(xid);
        }
    }

    public long getUid() {
        return uid;
    }
}

