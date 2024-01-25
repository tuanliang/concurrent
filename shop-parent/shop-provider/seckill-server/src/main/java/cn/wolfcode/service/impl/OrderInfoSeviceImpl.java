package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mapper.OrderInfoMapper;
import cn.wolfcode.mapper.PayLogMapper;
import cn.wolfcode.mapper.RefundLogMapper;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.IdGenerateUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Created by wolfcode-lanxw
 */
@Service
public class OrderInfoSeviceImpl implements IOrderInfoService {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private PayLogMapper payLogMapper;
    @Autowired
    private RefundLogMapper refundLogMapper;

    @Override
    public OrderInfo findByPhoneAndSeckillId(String userPhone, Long seckillId) {
        return orderInfoMapper.findByPhoneAndSeckillId(userPhone,seckillId);
    }

    @Override
    @Transactional
    public OrderInfo doSeckill(String userPhone, SeckillProductVo seckillProductVo) {
        // 4.扣减数据库库存
        int effectCount = seckillProductService.dercStockCount(seckillProductVo.getId());
        if(effectCount==0){
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
        // 5.创建秒杀订单
        OrderInfo orderInfo = createOrderInfo(userPhone,seckillProductVo);
        // 在redis中设置set集合，存储的是抢到商品用户的手机号码
        String orderSetKey = SeckillRedisKey.SECKILL_ORDER_SET.getRealKey(String.valueOf(seckillProductVo.getId()));
        redisTemplate.opsForSet().add(orderSetKey,userPhone);
        return  orderInfo;
    }

    @Override
    public OrderInfo findByOrderNo(String orderNo) {
        return orderInfoMapper.find(orderNo);
    }

    private OrderInfo createOrderInfo(String userPhone, SeckillProductVo seckillProductVo) {
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(seckillProductVo,orderInfo);
        orderInfo.setUserId(Long.parseLong(userPhone));
        orderInfo.setCreateDate(new Date());
        orderInfo.setDeliveryAddrId(1L);
        orderInfo.setSeckillDate(seckillProductVo.getStartDate());
        orderInfo.setSeckillTime(seckillProductVo.getTime());
        orderInfo.setOrderNo(String.valueOf(IdGenerateUtil.get().nextId()));
        orderInfo.setSeckillId(seckillProductVo.getId());
        orderInfoMapper.insert(orderInfo);
        return orderInfo;
    }

    @Override
    @Transactional
    public void cancelOrder(String orderNo) {
        System.out.println("超时取消订单开始。。。");

        OrderInfo orderInfo = orderInfoMapper.find(orderNo);
        // 判断订单是否处于未付款状态
        if(OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())){
            // 修改订单状态
            int effectCount = orderInfoMapper.updateCancelStatus(orderNo, OrderInfo.STATUS_TIMEOUT);
            if(effectCount==0){
                return;
            }
            // 真实库存回补
            seckillProductService.incrStockCount(orderInfo.getSeckillId());
            // 预库存回补
            seckillProductService.syncStockToRedis(orderInfo.getSeckillTime(),orderInfo.getSeckillId());
        }
        System.out.println("超时取消订单结束。。。");

    }
}
