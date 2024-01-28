package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.domain.AccountTransaction;
import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.mapper.AccountTransactionMapper;
import cn.wolfcode.mapper.UsableIntegralMapper;
import cn.wolfcode.service.IUsableIntegralService;
import cn.wolfcode.web.msg.IntergralCodeMsg;
import com.alibaba.fastjson.JSON;
import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Created by shiyi
 */
@Service
public class UsableIntegralServiceImpl implements IUsableIntegralService {
    @Autowired
    private UsableIntegralMapper usableIntegralMapper;
    @Autowired
    private AccountTransactionMapper accountTransactionMapper;

    @Override
    public void decrIntegral(OperateIntergralVo vo) {
        int effectCount = usableIntegralMapper.decrIntergral(vo.getUserId(),vo.getValue());
        if(effectCount==0){
            throw new BusinessException(IntergralCodeMsg.INTERGRAL_NOT_ENOUGH);
        }
    }

    @Override
    public void incrIntegral(OperateIntergralVo vo) {
        System.out.println("积分退款"+vo.getValue());
        usableIntegralMapper.incrIntergral(vo.getUserId(),vo.getValue());
    }

    @Override
    @GlobalTransactional
    public void decrIntegralTry(OperateIntergralVo vo, BusinessActionContext context) {
        System.out.println("执行try方法");
        // 插入事务控制表
        AccountTransaction log = new AccountTransaction();
        Date now = new Date();
        log.setAmount(vo.getValue());
        log.setUserId(vo.getUserId());
        log.setTxId(context.getXid());//全局事务ID
        log.setActionId(context.getBranchId());// 分支事务ID
        log.setGmtCreated(now);
        log.setGmtModified(now);
        accountTransactionMapper.insert(log);
        // 执行业务逻辑--》减积分
        int effectCount = usableIntegralMapper.decrIntergral(vo.getUserId(), vo.getValue());
        if(effectCount==0){
            throw new BusinessException(IntergralCodeMsg.INTERGRAL_NOT_ENOUGH);
        }
    }

    @Override
    public void decrIntegralCommit(BusinessActionContext context) {
        System.out.println("执行commit方法");
        // 查询事务记录
        AccountTransaction accountTransaction = accountTransactionMapper.get(context.getXid(), context.getBranchId());
        if(accountTransaction==null){
            // 如果为空--》写MQ通知管理员
        }else{
            // 如果不为空：
            if(AccountTransaction.STATE_TRY==accountTransaction.getState()){
                //状态为try执行commit逻辑
                // 跟新日志状态,空操作
                int effectCount = accountTransactionMapper.updateAccountTransactionState(context.getXid(), context.getBranchId(), AccountTransaction.STATE_COMMIT, AccountTransaction.STATE_TRY);

            }else if(AccountTransaction.STATE_COMMIT==accountTransaction.getState()){
                // 状态为commit不做事情
            }else{
//                状态为其他写MQ通知管理员
            }
        }

    }

    @Override
    @Transactional
    public void decrIntegralRollback(BusinessActionContext context) {
        System.out.println("执行rollback方法");
        // 查询事务记录
        AccountTransaction accountTransaction = accountTransactionMapper.get(context.getXid(), context.getBranchId());
        if(accountTransaction!=null){
            // 存在日志记录
            if(AccountTransaction.STATE_TRY==accountTransaction.getState()){
                // 处于try状态，将状态修改为cancel状态
                accountTransactionMapper.updateAccountTransactionState(context.getXid(),context.getBranchId(),AccountTransaction.STATE_COMMIT,AccountTransaction.STATE_TRY);
                // 执行cancel业务逻辑，添加积分

                usableIntegralMapper.incrIntergral(accountTransaction.getUserId(),accountTransaction.getAmount());
            }else if(AccountTransaction.STATE_COMMIT==accountTransaction.getState()){
                // 之前执行过cancel，幂等处理
            }else{
                // 其他情况，通知管理员
            }
        }else {
            // 插入事务控制表
            String str = (String) context.getActionContext("vo");
            System.out.println("存储的上下问对象："+str);
            OperateIntergralVo vo = JSON.parseObject(str,OperateIntergralVo.class);
            AccountTransaction log = new AccountTransaction();
            Date now = new Date();
            log.setAmount(vo.getValue());
            log.setUserId(vo.getUserId());
            log.setTxId(context.getXid());//全局事务ID
            log.setActionId(context.getBranchId());// 分支事务ID
            log.setGmtCreated(now);
            log.setGmtModified(now);
            log.setState(AccountTransaction.STATE_CANCEL);
            accountTransactionMapper.insert(log);
        }
    }
}
