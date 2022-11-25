package backend.dm.pageIndex;

public class PageInfo {
    public int pgno;
    public int freeSpace;
//页号和空闲空间大小
    public PageInfo(int pgno, int freeSpace) {
        this.pgno = pgno;
        this.freeSpace = freeSpace;
    }
}
