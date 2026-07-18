package com.yzytech.scx.delivery.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzytech.scx.common.core.util.Result;
import com.yzytech.scx.delivery.entity.AdPlatformConfig;
import com.yzytech.scx.delivery.service.AdPlatformConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/ad/platform-configs") public class AdPlatformConfigController { private final AdPlatformConfigService service;
 @GetMapping @SaCheckPermission("ad:config:read") public Result<Page<AdPlatformConfig>> page(Page<AdPlatformConfig> p,@RequestParam String mediaId,String keyword){try{return Result.success(service.page(p,mediaId,keyword));}catch(IllegalArgumentException e){return Result.fail(e.getMessage());}}
 @PostMapping @SaCheckPermission("ad:config:write") public Result<Boolean> save(@Valid @RequestBody AdPlatformConfig v){return call(()->service.save(v));}
 @PutMapping("/{id}") @SaCheckPermission("ad:config:write") public Result<Boolean> update(@PathVariable String id,@Valid @RequestBody AdPlatformConfig v){v.setId(id);return call(()->service.save(v));}
 private Result<Boolean> call(java.util.concurrent.Callable<Boolean> a){try{return Result.success(a.call());}catch(IllegalArgumentException e){return Result.fail(e.getMessage());}catch(Exception e){return Result.fail("配置保存失败");}}
}
