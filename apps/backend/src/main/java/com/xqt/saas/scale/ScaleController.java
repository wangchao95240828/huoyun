package com.xqt.saas.scale;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 复刻 ACC acc/api/Scale.php：电子秤设备接口（无 JWT，设备用 hid 自鉴权）。
 *
 *   POST /api/device/scale/{pluginCode}          → Goodscan doReceive：接收称重报文
 *   GET  /api/device/scale/{pluginCode}/records  → Goodscan readData：列未签入记录
 *
 * 路径在 SecurityConfig permitAll，设备身份由 ScaleService 内的 hid 校验保证。
 * 接收 @RequestBody String 原始报文（保留字节用于 md5 幂等，对齐 Goodscan）。
 */
@RestController
@RequestMapping("/api/device/scale")
public class ScaleController {
    private final ScaleService scaleService;

    public ScaleController(ScaleService scaleService) {
        this.scaleService = scaleService;
    }

    @PostMapping("/{pluginCode}")
    public Map<String, Object> receive(@PathVariable String pluginCode,
                                       @RequestBody(required = false) String rawBody) {
        return scaleService.receive(pluginCode, rawBody);
    }

    @GetMapping("/{pluginCode}/records")
    public List<Map<String, Object>> records(@PathVariable String pluginCode,
                                             @RequestParam(required = false, defaultValue = "") String hid) {
        return scaleService.readUnbound(pluginCode, hid);
    }
}
