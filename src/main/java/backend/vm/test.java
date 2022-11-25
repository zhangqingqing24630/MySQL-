package backend.vm;

import backend.dm.DataManager;
import backend.tm.TransactionManager;
import backend.tm.TransactionManagerImpl;

public class test {
    public static void main(String[] args) throws Exception {
        TransactionManagerImpl tm= TransactionManager.create("cun/tm");
        DataManager dm=DataManager.create("cun/dm",1 << 20,tm);
        VersionManager vm=VersionManager.newVersionManager(tm,dm);
        byte b[]=new byte[1122];
        //insert() 则是将数据包裹成 Entry，无脑交给 DM 插入即可：
        //level隔离级别，0代表读已提交
        long xid11=vm.begin(1);
        long uid11=vm.insert(xid11,b);
        vm.commit(xid11);
        long xid12=vm.begin(1);
        long uid12=vm.insert(xid12,b);
        vm.commit(xid12);
        long xid2=vm.begin(1);
        long xid3=vm.begin(1);
        //死锁
        vm.delete(xid2,uid11);
        vm.delete(xid3,uid12);
        vm.delete(xid2,uid12);
        vm.delete(xid3,uid11);
    }
}
