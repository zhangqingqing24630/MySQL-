package backend.im;
import backend.dm.cache.SubArray;
import backend.dm.DataManager;
import backend.dm.dataItem.DataItem;
import backend.tm.TransactionManagerImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import backend.im.Node.*;
import jdk.swing.interop.SwingInterOpUtils;
import utils.Parser;
//索引管理器，为 MYDB 提供了基于 B+ 树的非聚簇索引
public class BPlusTree {
    DataManager dm;
    long bootUid;
    DataItem bootDataItem;
    Lock bootLock;
    //生成一个空的根节点   ，返回存放根节点的uid
    public static long create(DataManager dm) throws Exception {
        //生成一个空的根节点
        byte[] rawRoot = Node.newNilRootRaw();
        //注意，这里dataItem里的data存的是一个Node,rootuid是根节点的uid
        long rootUid = dm.insert(TransactionManagerImpl.SUPER_XID, rawRoot);
        //System.out.println("rootuid是"+rootUid);
        //往dm中插入，返回存放rootuid的uid
        return dm.insert(TransactionManagerImpl.SUPER_XID, Parser.long2Byte(rootUid));
    }
    //以该uid为bootUid的节点为根节点建立一个B+树
    public static BPlusTree load(long bootUid, DataManager dm) throws Exception {
        //由于 B+ 树在插入删除时，会动态调整，根节点不是固定节点，
        // 于是设置一个 bootDataItem，该 bootDataItem 中存储了根节点的 UID
        DataItem bootDataItem = dm.read(bootUid);
        assert bootDataItem != null;
        BPlusTree t = new BPlusTree();
        t.bootUid = bootUid;
        t.bootDataItem = bootDataItem;
        t.bootLock = new ReentrantLock();
        t.dm=dm;
        return t;
    }

//left 原来的根节点uid
// right，新的分裂出来的节点的uid
// rightKey新的分裂出来的节点的第一个索引
    private void updateRootUid(long left, long right, long rightKey) throws Exception {
        //System.out.println("key"+rightKey);
        bootLock.lock();
        try {
            //生成一个根节点
            byte[] rootRaw = Node.newRootRaw(left, right, rightKey);
            //返回要插入的根节点的uid
            long newRootUid = dm.insert(TransactionManagerImpl.SUPER_XID, rootRaw);
            bootDataItem.before();
            SubArray diRaw = bootDataItem.data();
            //替换uid为新的uid
            System.arraycopy(Parser.long2Byte(newRootUid), 0, diRaw.raw, diRaw.start, 8);
            bootDataItem.after(TransactionManagerImpl.SUPER_XID);
        } finally {
            bootLock.unlock();
        }
    }
//从uid为nodeUid的节点开始寻找索引为key的数据的uid(直到找到叶子节点)
    private long searchLeaf(long nodeUid, long key) throws Exception {
        Node node = Node.loadNode(this, nodeUid);
        boolean isLeaf = node.isLeaf();
        node.release();
        if(isLeaf) {
            //叶子节点
            return nodeUid;
        } else {
            //找到索引为key的uid，继续往下搜索，直到搜到叶子节点
            long next = searchNext(nodeUid, key);
            return searchLeaf(next, key);
        }
    }
//(同层查找)在当前大节点选择对应key的uid，找不到则到邻节点继续查找，直到找到为止，返回的是大节点里面的key的uid
    private long searchNext(long nodeUid, long key) throws Exception {
        while(true) {
            //定位到nodeUid的这个节点
            Node node = Node.loadNode(this, nodeUid);
            //在当前节点node中返回对应key的uid，找不到则返回邻节点的uid
            SearchNextRes res = node.searchNext(key);
            //System.out.println(res.uid);
            node.release();
            //找到了,就在该节点，返回该节点的uid
            if(res.uid != 0) return res.uid;
            //没找到，继续查找
            nodeUid = res.siblingUid;
        }
    }

    public List<Long> search(long key) throws Exception {
        return searchRange(key, key);
    }
//返回的是数据uid
    public List<Long> searchRange(long leftKey, long rightKey) throws Exception {
        long rootUid = rootUid();
        //从uid为rootUid的节点开始寻找索引为leftKey的数据的叶子节点的uid
        long leafUid = searchLeaf(rootUid, leftKey);
        List<Long> uids = new ArrayList<>();
        while(true) {
            Node leaf = Node.loadNode(this, leafUid);
            LeafSearchRangeRes res = leaf.leafSearchRange(leftKey, rightKey);
            leaf.release();
            uids.addAll(res.uids);
            if(res.siblingUid == 0) {
                break;
            } else {
                leafUid = res.siblingUid;
            }
        }
        return uids;
    }
    //返回根节点Node（这个根节点一直在变）
    private long rootUid() {
        bootLock.lock();
        try {
            SubArray sa = bootDataItem.data();
            return Parser.parseLong(Arrays.copyOfRange(sa.raw, sa.start, sa.start+8));
        } finally {
            bootLock.unlock();
        }
    }
//从根节点开始查找，插入一个新节点
    public void insert(long key, long uid) throws Exception {
        long rootUid = rootUid();
        //从rootUid节点（一直在变,但永远是bootItem里的值）开始找到要插入的叶子节点位置插入
        InsertRes res = insert(rootUid, uid, key);
        assert res != null;
        //原节点已满，分裂出一个新的节点，则生成一个根节点，根节点的key保存原节点和新节点的key|uid。
        if(res.newNode != 0) {
            System.out.println("根节点已满，需要生成一个新的根节点");
            updateRootUid(rootUid, res.newNode, res.newKey);
        }
    }

    class InsertRes {
        long newNode, newKey;
    }
    //从nodeUid节点开始找到要插入的位置插入
    //如果nodeUid是叶子节点，直接插入
    //如果nodeUid不是叶子节点，在下一层找到叶子节点再插入
    private InsertRes insert(long nodeUid, long uid, long key) throws Exception {
        //从哪个节点开始遍历插入的位置
        Node node = Node.loadNode(this, nodeUid);
        boolean isLeaf = node.isLeaf();
        node.release();
        InsertRes res = null;
        System.out.println("找到的节点是不是叶子节点"+isLeaf);
        //如果nodeUid是叶子节点
        if(isLeaf) {
            //往nodeUid大节点处存key|uid信息
            //如果分裂，返回分裂出的节点的信息
            res = insertAndSplit(nodeUid, uid, key);
        } else {
            //（同层查找）在当前节点查找，找不到就返回邻节点查找，返回找到的key的uid或者邻节点的uid
            long next = searchNext(nodeUid, key);
            //如果不是叶子节点，继续在下一层搜索直到找到对应的叶子节点位置，当叶子节点满时，返回新分裂出的节点位置。
            InsertRes ir = insert(next, uid, key);
            //此时把分裂节点插入到一个非叶子节点上
            if(ir.newNode != 0) {
                //若分裂出节点，把分裂节点的key|uid添加到对应的父节点中。
                //如果原来的父节点已满，就会重新分裂出一个父节点，会返回分裂出的父节点。
                res = insertAndSplit(nodeUid, ir.newNode, ir.newKey);
            } else {
                res = new InsertRes();
            }
        }
        return res;
    }
    //从nodeUid节点开始找到要插入的位置插入uid|key信息，如果分裂，返回分裂出的节点
    private InsertRes insertAndSplit(long nodeUid, long uid, long key) throws Exception {
        while(true) {
            //找到nodeUid节点
            Node node = Node.loadNode(this, nodeUid);
            //如果新插入的节点在当前节点的最后，且当前节点已经有邻节点，返回邻节点,并插入到该邻节点
            InsertAndSplitRes iasr = node.insertAndSplit(uid, key);
            node.release();
            //一次插入不成功，返回邻接点继续插入
            if(iasr.siblingUid != 0) {
                nodeUid = iasr.siblingUid;
            } else {
                //成功插入节点后分裂了邻节点，返回存储邻节点的uid和开头索引
                InsertRes res = new InsertRes();
                res.newNode = iasr.newSon;
                res.newKey = iasr.newKey;
                return res;
            }
        }
    }

    public void close() {
        bootDataItem.release();
    }
}

