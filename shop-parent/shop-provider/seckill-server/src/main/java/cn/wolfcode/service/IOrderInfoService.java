package cn.wolfcode.service;


import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;

import java.util.Map;

/**
 * Created by wolfcode-lanxw
 */
public interface IOrderInfoService {

    /**
     * 根据用户手机号码和秒杀商品id查询订单信息
     * @param userPhone
     * @param seckillId
     * @return
     */
    OrderInfo findByPhoneAndSeckillId(String userPhone, Long seckillId);

    /**
     * 创建秒杀订单
     * @param userPhone
     * @param seckillProductVo
     * @return
     */
    OrderInfo doSeckill(String userPhone, SeckillProductVo seckillProductVo);

    /**
     * 根据订单号查询订单对象
     * @param orderNo
     * @return
     */
    OrderInfo findByOrderNo(String orderNo);

    /**
     * 根据订单号，取消订单
     * @param orderNo
     */
    void cancelOrder(String orderNo);

    /**
     * 获取支付服务返回的字符串
     * @param orderNo
     * @return
     */
    Result<String> payOnline(String orderNo);

    /**
     * 修改支付状态
     * @param orderNo
     * @param status
     * @param payType
     * @return
     */
    int changePayStatus(String orderNo, Integer status, int payType);

    /**
     * 进行在线退款
     * @param orderInfo
     */
    void refundOnline(OrderInfo orderInfo);
}
