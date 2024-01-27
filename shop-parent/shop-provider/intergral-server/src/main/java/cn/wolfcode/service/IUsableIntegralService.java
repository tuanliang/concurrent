package cn.wolfcode.service;

import cn.wolfcode.domain.OperateIntergralVo;

/**
 * Created by shiyi
 */
public interface IUsableIntegralService {
    /**
     * 积分扣减
     * @param vo
     */
    void decrIntegral(OperateIntergralVo vo);

    /**
     * 积分增加
     * @param vo
     */
    void incrIntegral(OperateIntergralVo vo);
}
