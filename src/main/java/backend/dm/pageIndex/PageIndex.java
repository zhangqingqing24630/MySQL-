package backend.dm.pageIndex;
import backend.dm.pageCache.PageCache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
//页面索引，缓存了每一页的空闲空间。用于在上层模块进行插入操作时，
// 能够快速找到一个合适空间的页面，而无需从磁盘或者缓存中检查每一个页面的信息。
public class PageIndex {
    //方法：将一页的空间划分成了 40 个区间。在启动时，就会遍历所有的页面信息，
    // 获取页面的空闲空间，安排到这 40 个区间中。
    // insert 在请求一个页时，会首先将所需的空间向上取整，映射到某一个区间，
    // 随后取出满足这个区间的任何一页，都可以满足需求。
    // 将一页划成40个区间
    private static final int INTERVALS_NO = 40;
    //一页中一个区间有多大
    private static final int THRESHOLD = PageCache.PAGE_SIZE / INTERVALS_NO;

    private Lock lock;
    //一个lists代表1页，被分为40个ArrayList，每个区间装的PageInfo（页号，剩余空间大小）
    private List<PageInfo>[] lists;

    @SuppressWarnings("unchecked")
    public PageIndex() {
        lock = new ReentrantLock();
        lists = new List[INTERVALS_NO+1];
        for (int i = 0; i < INTERVALS_NO+1; i ++) {
            lists[i] = new ArrayList<>();
        }
    }
// 在上层模块使用完这个页面后，需要将其重新插入 PageIndex：
    public void add(int pgno, int freeSpace) {
        lock.lock();
        try {
            int number = freeSpace / THRESHOLD;//该页面还剩多少个区间
            //lists[1]放还剩下一个区间的页号和剩余空间
            //lists[2]放还剩下二个区间的页号和剩余空间
            lists[number].add(new PageInfo(pgno, freeSpace));
        } finally {
            lock.unlock();
        }
    }
//从 PageIndex 中获取页面也很简单，算出区间号，直接取即可,
    public PageInfo select(int spaceSize) {
        lock.lock();
        try {
            int number = spaceSize / THRESHOLD;//选择spaceSize需要多大的区间才放得下
            if(number < INTERVALS_NO) number ++;//向上取整
            while(number <= INTERVALS_NO) {
                if(lists[number].size() == 0) {//没有这么大区间的页数
                    number ++;//没有正好这么大区间的页数，就把更大容量的给它
                    continue;
                }
                return lists[number].remove(0);//把该页数取走，填完后再重新插入
                //同一个页面是不允许并发写的
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

}

