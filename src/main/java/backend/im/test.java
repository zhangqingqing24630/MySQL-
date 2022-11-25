package backend.im;

import backend.dm.DataManager;
import backend.dm.DataManagerImpl;
import backend.tm.TransactionManager;
import backend.tm.TransactionManagerImpl;

public class test {
    public static void main(String[] args) throws Exception {
        TransactionManagerImpl tm=TransactionManager.create("cun/tm");
        DataManager dm=DataManager.create("cun/dm",1 << 20,tm);
        //1 生成一个空的根节点，存入到一个dataItem中，会返回一个存放dataItem的rootuid
        //再把rootuid存入，返回存放rootuid的uid
        long uid=BPlusTree.create(dm);
        System.out.println("存放rootuid的是"+uid);
        //2 以该空节点为根节点建立B+树
        //通过uid找到根节点的rootuid（在变），从而定位到根节点
        BPlusTree bt=BPlusTree.load(uid,dm);

        bt.insert(1,1);
        System.out.println("============");
        bt.insert(2,1);
        System.out.println("============");
        bt.insert(6,1);
        System.out.println("============");
        bt.insert(8,1);
        System.out.println("============");
        //验证插入到非叶子节点
        bt.insert(5,1);
        System.out.println("============");
        bt.insert(7,1);
        System.out.println("============");
        //System.out.println(bt.searchRange(10,12));
    }
}
