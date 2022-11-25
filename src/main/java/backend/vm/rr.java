package backend.vm;

import backend.dm.DataManager;
import backend.tm.TransactionManager;
import backend.tm.TransactionManagerImpl;

public class rr {
    public static void main(String[] args) throws Exception {
        TransactionManagerImpl tm= TransactionManager.create("cun/tm");
        DataManager dm=DataManager.create("cun/dm",1 << 20,tm);
        VersionManager vm=VersionManager.newVersionManager(tm,dm);
        byte b[]=new byte[1122];
        long xid1=vm.begin(1);
        long xid2=vm.begin(1);
        long uid1=vm.insert(xid1,b);
        byte[] output=vm.read(xid2,uid1);
        System.out.println(output);
        vm.commit(xid1);
        output=vm.read(xid2,uid1);
        System.out.println(output);
    }
}
