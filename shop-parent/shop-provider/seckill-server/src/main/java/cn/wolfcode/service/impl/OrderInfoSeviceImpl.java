package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.*;
import cn.wolfcode.mapper.OrderInfoMapper;
import cn.wolfcode.mapper.PayLogMapper;
import cn.wolfcode.mapper.RefundLogMapper;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.IdGenerateUtil;
import cn.wolfcode.web.feign.IntegralFeignApi;
import cn.wolfcode.web.feign.PayFeignApi;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    @Autowired
    private PayFeignApi payFeignApi;
    @Autowired
    private IntegralFeignApi integralFeignApi;

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

        return  orderInfo;
    }

    @Override
    public OrderInfo findByOrderNo(String orderNo) {
//        orderInfoMapper.find(orderNo)
        // 从redis中查询
        System.out.println("进入redis查订单");
        String orderHashKey = SeckillRedisKey.SECKILL_ORDER_HASH.getRealKey("");
        String objStr = (String) redisTemplate.opsForHash().get(orderHashKey, orderNo);
        return JSON.parseObject(objStr,OrderInfo.class);
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

    @Value("${pay.returnUrl}")
    private String returnUrl;
    @Value("${pay.notifyUrl}")
    private String notifyUrl;
    @Override
    public Result<String> payOnline(String orderNo) {
        // 根据订单号查询订单对象
        OrderInfo orderInfo = this.findByOrderNo(orderNo);
        if(OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())){
            PayVo payVo = new PayVo();
            payVo.setBody(orderInfo.getProductName());
            payVo.setSubject(orderInfo.getProductName());
            payVo.setOutTradeNo(orderNo);
            payVo.setTotalAmount(String.valueOf(orderInfo.getIntergral()));
            payVo.setReturnUrl(returnUrl);
            payVo.setNotifyUrl(notifyUrl);
            Result<String>result=payFeignApi.payOnline(payVo);
            return result;
        }
        return Result.error(SeckillCodeMsg.PAY_STATUS_CHANGE);
    }

    @Override
    public int changePayStatus(String orderNo, Integer status, int payType) {
        return orderInfoMapper.changePayStatus(orderNo,status,payType);
    }

    @Override
    public void refundOnline(OrderInfo orderInfo) {
        RefundVo refundVo = new RefundVo();
        refundVo.setOutTradeNo(orderInfo.getOrderNo());
        refundVo.setRefundAmount(String.valueOf(orderInfo.getSeckillPrice()));
        refundVo.setRefundReason("不想要了");
        Result<Boolean>result = payFeignApi.refund(refundVo);
        if(result==null||result.hasError()||!result.getData()){
            throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
        }
        orderInfoMapper.changeRefundStatus(orderInfo.getOrderNo(),OrderInfo.STATUS_REFUND);
    }

    @Override
    @GlobalTransactional
    public void payIntegral(String orderNo) {
        OrderInfo orderInfo = this.findByOrderNo(orderNo);
        if(orderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())){
            // 处于未支付状态
            PayLog payLog = new PayLog();
            payLog.setOrderNo(orderNo);
            payLog.setPayTime(new Date());
            payLog.setTotalAmount((String.valueOf(orderInfo.getSeckillPrice())));
            payLog.setPayType(OrderInfo.PAYTYPE_INTERGRAL);
            payLogMapper.insert(payLog);
            // 远程调用积分服务完成积分扣减
            OperateIntergralVo vo = new OperateIntergralVo();
            vo.setUserId(orderInfo.getUserId());
            vo.setValue(orderInfo.getIntergral());
            // 调用积分服务
            Result result = integralFeignApi.decrIntegral(vo);
            if(result==null||result.hasError()){
                throw new BusinessException(SeckillCodeMsg.INTERGRAL_SERVER_ERROR);
            }
            // 修改订单状态
            int effectCount = orderInfoMapper.changePayStatus(orderNo, OrderInfo.STATUS_ACCOUNT_PAID, OrderInfo.PAYTYPE_INTERGRAL);
            if(effectCount==0){
                throw new BusinessException(SeckillCodeMsg.PAY_ERROR);
            }
        }
    }

    @Override
    @GlobalTransactional
    public void refundIntegral(OrderInfo orderInfo) {
        if(OrderInfo.STATUS_ACCOUNT_PAID.equals(orderInfo.getStatus())){
            RefundLog log = new RefundLog();
            log.setOrderNo(orderInfo.getOrderNo());
            log.setRefundReason("不想要了");
            log.setRefundAmount(orderInfo.getIntergral());
            log.setRefundTime(new Date());
            log.setRefundType(OrderInfo.PAYTYPE_INTERGRAL);
            refundLogMapper.insert(log);
            // 远程调用服务
            OperateIntergralVo vo = new OperateIntergralVo();
            vo.setUserId(orderInfo.getUserId());
            vo.setValue(orderInfo.getIntergral());
            // 调用积分服务
            Result result = integralFeignApi.incrIntegral(vo);
            if(result==null||result.hasError()){
                throw new BusinessException(SeckillCodeMsg.INTERGRAL_SERVER_ERROR);
            }
            // 修改订单状态
            int effectCount = orderInfoMapper.changeRefundStatus(orderInfo.getOrderNo(),OrderInfo.STATUS_REFUND)    ;
            if(effectCount==0){
                throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
            }
        }
    }
}
