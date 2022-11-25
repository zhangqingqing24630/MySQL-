package backend.parser;

import backend.dm.DataManager;
import backend.parser.statement.Create;
import backend.parser.statement.Insert;
import backend.server.Executor;
import backend.tbm.TableManager;
import backend.tm.TransactionManager;
import backend.tm.TransactionManagerImpl;
import backend.vm.VersionManager;

import java.nio.charset.StandardCharsets;

public class tbm_test {
    public static void main(String[] args) throws Exception {
        TransactionManagerImpl tm= TransactionManager.open("cun/tm");
        DataManager dm=DataManager.open("cun/dm",1 << 20,tm);
        VersionManager vm=VersionManager.newVersionManager(tm,dm);
        TableManager tbm=TableManager.open("cun/",vm,dm);
        Executor executor=new Executor(tbm);
        //String ss=
        //String ss=" insert into student values 5 \"xiaohong\" 22";
//        byte b[]=ss.getBytes(StandardCharsets.UTF_8);
//        Object stat = Parser.Parse(b);
//        res = tbm.insert(xid, (Insert)stat);
        String ss="create table tb name string,id int32 (index name id)";
        byte b[]=ss.getBytes(StandardCharsets.UTF_8);
        Object stat = Parser.Parse(b);
        //tbm.create(1, (Create) stat);
    }
}
