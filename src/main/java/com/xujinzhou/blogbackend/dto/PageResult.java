package com.xujinzhou.blogbackend.dto;

import java.util.List;

/**
 * 分页结果封装
 *
 * 为什么不直接用 MyBatis-Plus 的 Page<T> 返回给前端？
 *   ① Page 里带了很多前端用不到的字段（如 optimizeCountSql、searchCount、
 *      orders、maxLimit 等内部状态），直接暴露出去会让接口响应臃肿
 *   ② 前后端接口应该由我们自己定义，而不是跟着 ORM 框架的结构走
 *      —— 万一以后换 ORM，接口不用改
 *
 * 字段说明：
 *   list      当前页的数据
 *   total     总记录数
 *   page      当前页码（从 1 开始）
 *   size      每页条数
 *   pages     总页数（total / size 向上取整）
 *   hasNext   是否还有下一页
 */
public class PageResult<T> {

    private List<T> list;
    private long total;
    private long page;
    private long size;
    private long pages;
    private boolean hasNext;

    // 无参构造：如果以后要把 PageResult 也放进缓存，这是必需的
    public PageResult() {
    }

    public PageResult(List<T> list, long total, long page, long size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = size > 0 ? (total + size - 1) / size : 0;   // 向上取整
        this.hasNext = page < this.pages;
    }

    /** 便利工厂方法：从 MyBatis-Plus 的 Page 对象转换过来 */
    public static <T> PageResult<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> p) {
        return new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }

    // getter
    public List<T> getList() { return list; }
    public long getTotal() { return total; }
    public long getPage() { return page; }
    public long getSize() { return size; }
    public long getPages() { return pages; }
    public boolean isHasNext() { return hasNext; }

    // setter
    public void setList(List<T> list) { this.list = list; }
    public void setTotal(long total) { this.total = total; }
    public void setPage(long page) { this.page = page; }
    public void setSize(long size) { this.size = size; }
    public void setPages(long pages) { this.pages = pages; }
    public void setHasNext(boolean hasNext) { this.hasNext = hasNext; }
}
