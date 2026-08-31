package com.SHADOW.P2P_SERVICE.Clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

// 🟢 STRICT ENFORCEMENT: No URL fallbacks allowed
@FeignClient(
        name = "USER-CATALOG-SERVICE",
        url = "${USER_CATALOG_URL}",
        path = "/api/users",
        configuration = FeignInterceptorConfig.class
)
public interface UserCatalogClient {

    @GetMapping("/internal/search-owners")
    List<Map<String, Object>> searchUsersByHandle(@RequestParam("username") String username);
}