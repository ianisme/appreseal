package com.appreseal.web;

import com.appreseal.common.ApiException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 团队版不再提供钱包、充值和套餐。旧地址统一拒绝，避免前端或脚本还按付费接口调用。
 */
@RestController
@RequestMapping("/api/v1")
public class WalletController {

    @RequestMapping({"/wallet", "/wallet/**", "/transactions", "/recharges", "/recharges/**", "/packages"})
    public void gone() {
        throw ApiException.badRequest("团队版不提供钱包和充值");
    }
}
