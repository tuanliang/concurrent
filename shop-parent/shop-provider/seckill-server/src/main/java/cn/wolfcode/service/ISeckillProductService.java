package cn.wolfcode.service;

import cn.wolfcode.domain.SeckillProduct;
import cn.wolfcode.domain.SeckillProductVo;

import java.util.List;

/**
 * Created by shiyi
 */
public interface ISeckillProductService {
    /**
     * 查询秒杀列表的数据
     * @param time
     * @return
     */
    List<SeckillProductVo> queryByTime(Integer time);

    /**
     * 根据秒杀场次和秒杀商品ID查询秒杀商品vo对象
     * @param time
     * @param seckillId
     * @return
     */
    SeckillProductVo find(Integer time, Long seckillId);

    /**
     * 根据秒杀商品id扣减库存
     * @param seckillId
     */
    int dercStockCount(Long seckillId);

    /**
     * 从缓冲中获取秒杀商品列表的集合
     * @param time
     * @return
     */
    List<SeckillProductVo> queryByTimeFromCache(Integer time);

    /**
     * 从缓冲中获取秒杀商品详情
     * @param time
     * @param seckillId
     * @return
     */
    SeckillProductVo findFromCache(Integer time, Long seckillId);

    /**
     * 查询数据库库存同步到redis
     * @param time
     * @param seckillId
     */
    void syncStockToRedis(Integer time, Long seckillId);

    /**
     * 增加库存
     * @param seckillId
     */
    void incrStockCount(Long seckillId);
}
