package cn.wolfcode.web.controller;


import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.web.feign.PayFeignApi;
import org.apache.ibatis.annotations.Results;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.Map;

/**
 * Created by shiyi
 */
@RestController
@RequestMapping("/orderPay")
@RefreshScope
public class OrderPayController {
    @Autowired
    private IOrderInfoService orderInfoService;
    @Autowired
    private PayFeignApi payFeignApi;

    @RequestMapping("/pay")
    public Result<String>pay(String orderNo,Integer type){
        if(OrderInfo.PAYTYPE_ONLINE.equals(type)){
            // 在线支付
            return orderInfoService.payOnline(orderNo);
        }else{
            // 积分支付
            orderInfoService.payIntegral(orderNo);
            return Result.success();
        }
    }
    @RequestMapping("/refund")
    public Result<String>refund(String orderNo){
        OrderInfo orderInfo = orderInfoService.findByOrderNo(orderNo);
        if(OrderInfo.PAYTYPE_ONLINE.equals(orderInfo.getPayType())){
            // 在线退款
            orderInfoService.refundOnline(orderInfo);
        }else{
            // 积分退款
                orderInfoService.refundIntegral(orderInfo);
        }
        return Result.success();
    }

    // 异步回调
    @RequestMapping("/notifyUrl")
    public String notifyUrl(@RequestParam Map<String,String>params){
        System.out.println("支付异步回调");
        Result<Boolean>result=payFeignApi.rsaCheckV1(params);
        if(result==null||result.hasError()){
            return "fail";
        }
        Boolean signVerified = result.getData();// 验证SDK签名
        if(signVerified){
            // 验签成功，修改订单状态
            String orderNo = params.get("out_trade_no");
            int effectCount = orderInfoService.changePayStatus(orderNo, OrderInfo.STATUS_ACCOUNT_PAID, OrderInfo.PAYTYPE_ONLINE);
            if(effectCount==0){
                // 发消息给客服，走退款逻辑
            }
        }else{
            // 验签失败
        }
        return "success";
    }
    @Value("${pay.errorUrl}")
    private String errorUrl;
    @Value("${pay.frontEndPayUrl}")
    private String frontEndPayUrl;
    // 同步回调
    @RequestMapping("/returnUrl")
    public void returnUrl(@RequestParam Map<String,String>params,HttpServletResponse response) throws IOException {
        System.out.println("支付同步回调");

        Result<Boolean> result = payFeignApi.rsaCheckV1(params);
        if(result==null||result.hasError()||!result.getData()){
            response.sendRedirect(errorUrl);
            return;
        }
        String orderNo = params.get("out_trade_no");
        response.sendRedirect(frontEndPayUrl+orderNo);
    }
}
