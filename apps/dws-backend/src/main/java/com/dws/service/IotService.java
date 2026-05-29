package com.dws.service;

import com.dws.dto.IotRequest;
import com.dws.dto.IotResponse;
import com.dws.exception.IotException;
import com.dws.repository.IotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IotService {

    private static final Logger logger = LoggerFactory.getLogger(IotService.class);

    private final IotRepository repository;

    @Value("${app.iot.secret-key:mei432qiao765wu168mnjsio}")
    private String secretKey;

    @Value("${app.iot.auth-window-seconds:900}")
    private int authWindowSeconds;

    @Value("${app.iot.default-tenant-id:}")
    private String defaultTenantId;

    /** 防重放缓存：token -> 过期时间戳（秒），窗口结束后自动失效 */
    private final ConcurrentHashMap<String, Long> nonceCache = new ConcurrentHashMap<>();

    public IotService(IotRepository repository) {
        this.repository = repository;
    }

    @Transactional(rollbackFor = Exception.class)
    public IotResponse process(IotRequest request) {
        logger.info("开始处理IoT请求: action={}, itemNumber={}, shipmentNumber={}", 
            request.getAction(), request.getItemNumber(), request.getShipmentNumber());
        try {
            validateAuth(request);
            IotResponse response = switch (request.getAction()) {
                case "check" -> processCheck(request);
                case "pickup" -> processPickup(request);
                case "update" -> processUpdate(request);
                default -> throw new IotException("IOT_INVALID_ACTION", "无效的action参数", "stop");
            };
            logger.info("IoT请求处理完成: status={}", response.getStatus());
            return response;
        } catch (Exception e) {
            logger.error("IoT请求处理失败: ", e);
            throw e;
        }
    }

    private void validateAuth(IotRequest request) {
        String token = request.getToken();
        String time = request.getTime();

        // 验证时间戳格式
        if (time == null || time.isEmpty()) {
            throw IotException.timestampExpired();
        }
        long requestTime;
        try {
            requestTime = Long.parseLong(time);
        } catch (NumberFormatException e) {
            throw IotException.timestampExpired();
        }

        // 验证签名：md5(秘钥 + 时间戳)，32位小写
        String expectedToken = md5(secretKey + time);
        if (token == null || !token.equals(expectedToken)) {
            throw IotException.authInvalid();
        }

        // 防重放：同一 token 在有效窗口内只允许使用一次
        long currentTime = System.currentTimeMillis() / 1000;
        Long usedAt = nonceCache.putIfAbsent(token, currentTime);
        if (usedAt != null) {
            // 如果旧记录已过期，允许覆盖
            if (currentTime - usedAt < authWindowSeconds) {
                throw IotException.duplicateRequest();
            }
            nonceCache.put(token, currentTime);
        }

        // 验证时间戳是否在有效窗口内
        if (Math.abs(currentTime - requestTime) > authWindowSeconds) {
            nonceCache.remove(token); // 清理无效记录
            throw IotException.timestampExpired();
        }

        // 定期清理过期 nonce（每 100 次请求触发一次）
        if (nonceCache.size() > 10000) {
            nonceCache.values().removeIf(v -> currentTime - v > authWindowSeconds);
        }
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5算法不可用", e);
        }
    }

    private IotResponse processCheck(IotRequest request) {
        String tenantId = getTenantId();
        String itemNumber = request.getItemNumber();
        
        if (itemNumber == null || itemNumber.isEmpty()) {
            throw IotException.itemNotFound();
        }

        Map<String, Object> carton = repository.findCartonByItemNumber(tenantId, itemNumber);
        
        String shipmentNo = request.getShipmentNumber() != null ? request.getShipmentNumber() : "IOT_" + System.currentTimeMillis();
        String shipmentId;
        String cartonId;
        
        if (carton == null) {
            String customerId = repository.findOrCreateDefaultCustomer(tenantId);
            shipmentId = repository.findOrCreateShipment(tenantId, customerId, shipmentNo);
            cartonId = repository.findOrCreateCarton(tenantId, shipmentId, itemNumber, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
            carton = buildCartonMap(cartonId, itemNumber, shipmentId, shipmentNo);
        } else {
            shipmentId = (String) carton.get("shipment_id");
            shipmentNo = (String) carton.get("shipment_no");
            cartonId = (String) carton.get("carton_id");
            
            if (request.getShipmentNumber() != null && !request.getShipmentNumber().isEmpty()) {
                if (!request.getShipmentNumber().equals(shipmentNo)) {
                    throw IotException.shipmentMismatch();
                }
            }
        }

        int totalCartons = repository.countCartonsByShipment(tenantId, shipmentId);

        List<IotResponse.OptionItem> options = buildCheckOptions(itemNumber, shipmentNo, carton, totalCartons);

        return IotResponse.builder()
                .status(1)
                .info("")
                .options(options)
                .printLabels(extractPrintLabels(request))
                .build();
    }

    public IotResponse processPickup(IotRequest request) {
        validateDimensions(request);
        
        String tenantId = getTenantId();
        String itemNumber = request.getItemNumber();

        Map<String, Object> carton = repository.findCartonByItemNumber(tenantId, itemNumber);
        
        String shipmentNo = request.getShipmentNumber() != null ? request.getShipmentNumber() : "IOT_" + System.currentTimeMillis();
        String shipmentId;
        String cartonId;
        
        if (carton == null) {
            String customerId = repository.findOrCreateDefaultCustomer(tenantId);
            shipmentId = repository.findOrCreateShipment(tenantId, customerId, shipmentNo);
            cartonId = repository.findOrCreateCarton(tenantId, shipmentId, itemNumber,
                    request.getWeight(), request.getLength(), request.getWidth(), request.getHeight());
            carton = buildCartonMap(cartonId, itemNumber, shipmentId, shipmentNo);
        } else {
            shipmentId = (String) carton.get("shipment_id");
            shipmentNo = (String) carton.get("shipment_no");
            cartonId = (String) carton.get("carton_id");
            
            if (request.getShipmentNumber() != null && !request.getShipmentNumber().isEmpty()) {
                if (!request.getShipmentNumber().equals(shipmentNo)) {
                    throw IotException.shipmentMismatch();
                }
            }

            repository.updateCartonDimensions(tenantId, cartonId,
                    request.getWeight(), request.getLength(), request.getWidth(), request.getHeight());
        }

        repository.insertScanEvent(tenantId, shipmentId, cartonId, "PICKUP", "OK",
                null, request.getPicUrl(), request.getPicBase64());

        int totalCartons = repository.countCartonsByShipment(tenantId, shipmentId);
        int scannedCount = repository.countScannedCartonsByShipment(tenantId, shipmentId);

        repository.updateShipmentMeasuredAt(tenantId, shipmentId);

        List<IotResponse.OptionItem> options = buildPickupOptions(itemNumber, shipmentNo, carton, totalCartons, scannedCount);
        
        String voiceText = String.format("拣货成功，当前 %d / %d", scannedCount, totalCartons);

        return IotResponse.builder()
                .status(1)
                .info("")
                .action("continue")
                .voiceText(voiceText)
                .options(options)
                .printLabels(extractPrintLabels(request))
                .build();
    }

    public IotResponse processUpdate(IotRequest request) {
        validateDimensions(request);
        
        String tenantId = getTenantId();
        String itemNumber = request.getItemNumber();
        
        Map<String, Object> carton = repository.findCartonByItemNumber(tenantId, itemNumber);
        
        String shipmentNo = request.getShipmentNumber() != null ? request.getShipmentNumber() : "IOT_" + System.currentTimeMillis();
        String shipmentId;
        String cartonId;
        
        if (carton == null) {
            String customerId = repository.findOrCreateDefaultCustomer(tenantId);
            shipmentId = repository.findOrCreateShipment(tenantId, customerId, shipmentNo);
            cartonId = repository.findOrCreateCarton(tenantId, shipmentId, itemNumber,
                    request.getWeight(), request.getLength(), request.getWidth(), request.getHeight());
            carton = buildCartonMap(cartonId, itemNumber, shipmentId, shipmentNo);
        } else {
            shipmentId = (String) carton.get("shipment_id");
            shipmentNo = (String) carton.get("shipment_no");
            cartonId = (String) carton.get("carton_id");

            repository.updateCartonDimensions(tenantId, cartonId,
                    request.getWeight(), request.getLength(), request.getWidth(), request.getHeight());
        }

        repository.insertScanEvent(tenantId, shipmentId, cartonId, "UPDATE", "OK",
                null, request.getPicUrl(), request.getPicBase64());

        int totalCartons = repository.countCartonsByShipment(tenantId, shipmentId);
        int scannedCount = repository.countScannedCartonsByShipment(tenantId, shipmentId);

        List<IotResponse.OptionItem> options = buildPickupOptions(itemNumber, shipmentNo, carton, totalCartons, scannedCount);

        return IotResponse.builder()
                .status(1)
                .info("")
                .action("continue")
                .voiceText("更新成功")
                .options(options)
                .printLabels(extractPrintLabels(request))
                .build();
    }

    private List<IotResponse.PrintLabel> extractPrintLabels(IotRequest request) {
        Map<String, Object> ext = request.getExt();
        if (ext == null || !ext.containsKey("printlabels")) {
            return null;
        }
        Object printlabelsObj = ext.get("printlabels");
        if (!(printlabelsObj instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> printlabels = (Map<String, Object>) printlabelsObj;

        List<IotResponse.PrintLabel> labels = new ArrayList<>();
        for (Map.Entry<String, Object> entry : printlabels.entrySet()) {
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) entry.getValue();

            IotResponse.PrintLabel.PrintLabelBuilder builder = IotResponse.PrintLabel.builder();

            if (config.containsKey("printer_name")) {
                builder.printerName(String.valueOf(config.get("printer_name")));
            }
            if (config.containsKey("qty")) {
                try {
                    builder.printQty(Integer.valueOf(String.valueOf(config.get("qty"))));
                } catch (NumberFormatException e) {
                    // 忽略无效的qty
                }
            }
            if (config.containsKey("type")) {
                builder.type(String.valueOf(config.get("type")));
            }
            if (config.containsKey("label_url")) {
                builder.labelUrl(String.valueOf(config.get("label_url")));
            }
            if (config.containsKey("label_base64")) {
                builder.labelBase64(String.valueOf(config.get("label_base64")));
            }

            labels.add(builder.build());
        }
        return labels.isEmpty() ? null : labels;
    }

    private void validateDimensions(IotRequest request) {
        if (request.getWeight() == null || request.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
            throw IotException.dimensionInvalid();
        }
        if (request.getLength() == null || request.getLength().compareTo(BigDecimal.ZERO) <= 0) {
            throw IotException.dimensionInvalid();
        }
        if (request.getWidth() == null || request.getWidth().compareTo(BigDecimal.ZERO) <= 0) {
            throw IotException.dimensionInvalid();
        }
        if (request.getHeight() == null || request.getHeight().compareTo(BigDecimal.ZERO) <= 0) {
            throw IotException.dimensionInvalid();
        }
    }

    private Map<String, Object> buildCartonMap(String cartonId, String itemNumber, String shipmentId, String shipmentNo) {
        Map<String, Object> carton = new HashMap<>();
        carton.put("carton_id", cartonId);
        carton.put("carton_no", itemNumber);
        carton.put("shipment_id", shipmentId);
        carton.put("shipment_no", shipmentNo);
        carton.put("channel_name", "");
        carton.put("channel_code", "");
        carton.put("destination_country", "");
        carton.put("destination_postal_code", "");
        return carton;
    }

    private List<IotResponse.OptionItem> buildCheckOptions(String itemNumber, String shipmentNo,
                                                           Map<String, Object> carton, int totalCartons) {
        List<IotResponse.OptionItem> options = new ArrayList<>();
        options.add(IotResponse.OptionItem.builder().label("箱号").value(itemNumber).build());
        options.add(IotResponse.OptionItem.builder().label("总箱数").value(totalCartons).build());
        options.add(IotResponse.OptionItem.builder().label("运单号").value(shipmentNo).build());
        
        String channelName = (String) carton.get("channel_name");
        options.add(IotResponse.OptionItem.builder().label("服务").value(channelName != null ? channelName : "").build());
        
        String channelCode = (String) carton.get("channel_code");
        options.add(IotResponse.OptionItem.builder().label("服务代码").value(channelCode != null ? channelCode : "").build());
        
        String destCountry = (String) carton.get("destination_country");
        options.add(IotResponse.OptionItem.builder().label("国家").value(destCountry != null ? destCountry : "").build());
        
        String destPostal = (String) carton.get("destination_postal_code");
        options.add(IotResponse.OptionItem.builder().label("邮编").value(destPostal != null ? destPostal : "").build());
        
        return options;
    }

    private List<IotResponse.OptionItem> buildPickupOptions(String itemNumber, String shipmentNo,
                                                            Map<String, Object> carton, int totalCartons, int scannedCount) {
        List<IotResponse.OptionItem> options = new ArrayList<>();
        options.add(IotResponse.OptionItem.builder().label("件数").value(scannedCount + "/" + totalCartons).build());
        options.add(IotResponse.OptionItem.builder().label("箱号").value(itemNumber).build());
        options.add(IotResponse.OptionItem.builder().label("总箱数").value(totalCartons).build());
        options.add(IotResponse.OptionItem.builder().label("运单号").value(shipmentNo).build());
        
        String channelName = (String) carton.get("channel_name");
        options.add(IotResponse.OptionItem.builder().label("服务").value(channelName != null ? channelName : "").build());
        
        String channelCode = (String) carton.get("channel_code");
        options.add(IotResponse.OptionItem.builder().label("服务代码").value(channelCode != null ? channelCode : "").build());
        
        String destCountry = (String) carton.get("destination_country");
        options.add(IotResponse.OptionItem.builder().label("国家").value(destCountry != null ? destCountry : "").build());
        
        String destPostal = (String) carton.get("destination_postal_code");
        options.add(IotResponse.OptionItem.builder().label("邮编").value(destPostal != null ? destPostal : "").build());
        
        return options;
    }

    private String getTenantId() {
        if (defaultTenantId != null && !defaultTenantId.isEmpty()) {
            return defaultTenantId;
        }
        return "00000000-0000-0000-0000-000000000000";
    }

}
