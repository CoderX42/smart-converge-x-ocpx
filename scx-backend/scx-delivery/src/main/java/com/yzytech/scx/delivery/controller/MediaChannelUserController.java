package com.yzytech.scx.delivery.controller;
import cn.dev33.satoken.annotation.SaCheckPermission; import com.baomidou.mybatisplus.extension.plugins.pagination.Page; import com.yzytech.scx.common.core.util.Result; import com.yzytech.scx.delivery.entity.MediaChannelUser; import com.yzytech.scx.delivery.service.MediaChannelUserService; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/ad/channel-users") public class MediaChannelUserController {private final MediaChannelUserService service;
 @GetMapping @SaCheckPermission("ad:channel-user:read") public Result<Page<MediaChannelUser>> page(Page<MediaChannelUser> p,@RequestParam String mediaId,String channelId,String keyword,String status){try{return Result.success(service.page(p,mediaId,channelId,keyword,status));}catch(IllegalArgumentException e){return Result.fail(e.getMessage());}}
 @PostMapping @SaCheckPermission("ad:channel-user:write") public Result<Boolean> save(@Valid @RequestBody MediaChannelUser v){return call(()->service.save(v));}
 @PutMapping("/{id}") @SaCheckPermission("ad:channel-user:write") public Result<Boolean> update(@PathVariable String id,@Valid @RequestBody MediaChannelUser v){v.setId(id);return call(()->service.save(v));}
 private Result<Boolean> call(java.util.concurrent.Callable<Boolean> a){try{return Result.success(a.call());}catch(IllegalArgumentException e){return Result.fail(e.getMessage());}catch(Exception e){return Result.fail("渠道用户保存失败");}}
}
