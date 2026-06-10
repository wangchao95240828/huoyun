package com.xqt.saas.customerapi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.xqt.saas.common.ApiException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Submit 前置校验器。对应 ACC config/online/UPS_New.php 和 inc/base.php 里的字段级校验。
 *
 * 之所以放前置（gateway.submit 之前）：UPS 拒单返回 120307 / 128101 / 1824 / 1827 / 711 等英文错码，
 * 用户体验差。前置拦截能给出准确的中文错误，且避免无谓的 UPS API 调用。
 *
 * 规则对照 ACC：
 * - UPS_New.php createOrder L371-374, L711-715, L1824-1829, L1850-1856
 * - base.php L538-574 (US/CA 省州白名单, 重量/件数边界, 申报金额自洽)
 */
@Component
public class SubmitValidator {

    private final JdbcTemplate jdbc;

    public SubmitValidator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final Pattern HSCODE_PATTERN = Pattern.compile("^[A-Za-z0-9.]{6,15}$");
    private static final Pattern COUNTRY_CODE_PATTERN = Pattern.compile("^[A-Z]{2}$");

    // ACC base.php 538-545: US/CA UPS 制单时省/州必须是有效二字码
    // 50 states + DC + AS/GU/MP/PR/VI 5 territories
    private static final Set<String> US_STATES = Set.of(
        "AL","AK","AS","AZ","AR","CA","CO","CT","DE","DC","FL","GA","GU","HI","ID","IL","IN",
        "IA","KS","KY","LA","ME","MD","MA","MI","MN","MP","MS","MO","MT","NE","NV","NH","NJ",
        "NM","NY","NC","ND","OH","OK","OR","PA","PR","RI","SC","SD","TN","TX","UT","VT","VI",
        "VA","WA","WV","WI","WY"
    );
    // 10 provinces + 3 territories
    private static final Set<String> CA_PROVINCES = Set.of(
        "AB","BC","MB","NB","NL","NS","ON","PE","QC","SK","NT","NU","YT"
    );
    // 爱尔兰行政区代码（ACC base.php 也校验 IE）
    private static final Set<String> IE_COUNTIES = Set.of(
        "CW","CN","CE","C","DL","D","G","KY","KE","KK","LS","LM","LK","LD","LH","MO","MH",
        "MN","OY","RN","SO","TA","WD","WH","WX","WW"
    );

    /**
     * 主入口。在 Submit 链路里 gateway.submit() 之前调用。
     *
     * @param channelCode  渠道码，如 "UPS-GROUND-US"
     * @param providerCode 承运商，如 "UPS"
     * @param accCompat    订单 metadata.acc_compat（含 receiver/declare/country 等）
     * @param weight       计费重 (kg)
     * @param piece        件数
     */
    public void validate(String channelCode, String providerCode,
                         Map<String, Object> accCompat,
                         BigDecimal weight, Integer piece) {
        validateCommon(weight, piece);

        // ACC _Product.MaxWeight / MaxLength：承运商级硬限（如 UPS Ground 70kg / 274cm）
        if (channelCode != null) {
            validateChannelHardLimits(channelCode, weight, accCompat);
        }

        if ("UPS".equalsIgnoreCase(providerCode)) {
            validateUps(accCompat);
        }
    }

    /** 查 channels.max_weight_kg / max_length_cm，超过即拒。 */
    private void validateChannelHardLimits(String channelCode, BigDecimal weight,
                                           Map<String, Object> accCompat) {
        Map<String, Object> limits;
        try {
            limits = jdbc.queryForMap(
                "SELECT max_weight_kg, max_length_cm FROM channels WHERE code = ? LIMIT 1",
                channelCode);
        } catch (EmptyResultDataAccessException ex) {
            return; // 未知渠道交由后续 RateEngine 处理
        }
        BigDecimal maxWeight = bd(limits.get("max_weight_kg"));
        if (maxWeight != null && maxWeight.signum() > 0 && weight.compareTo(maxWeight) > 0) {
            throw ApiException.badRequest(
                "渠道 " + channelCode + " 单件最大重量 " + maxWeight + " kg（ACC _Product.MaxWeight），当前 " + weight + " kg"); // ACC FreightClass L1342-1345
        }

        BigDecimal maxLength = bd(limits.get("max_length_cm"));
        if (maxLength != null && maxLength.signum() > 0) {
            BigDecimal maxDim = packagesMaxDimension(accCompat.get("packageList"));
            if (maxDim != null && maxDim.compareTo(maxLength) > 0) {
                throw ApiException.badRequest(
                    "渠道 " + channelCode + " 单件最大长度 " + maxLength + " cm（ACC _Product.MaxLength），当前最长边 " + maxDim + " cm");
            }
        }
    }

    /** packageList 里所有箱子的 max(length, width, height) 取最大。 */
    private BigDecimal packagesMaxDimension(Object packageList) {
        BigDecimal max = null;
        for (Map<String, Object> pkg : listOfMaps(packageList)) {
            for (String dim : new String[]{"length", "width", "height"}) {
                BigDecimal v = bd(pkg.get(dim));
                if (v != null && (max == null || v.compareTo(max) > 0)) max = v;
            }
        }
        return max;
    }

    // ─── 通用边界（所有承运商都适用） ───
    private void validateCommon(BigDecimal weight, Integer piece) {
        if (weight == null || weight.signum() <= 0) {
            throw ApiException.badRequest("重量必须大于零");                 // ACC base.php 564
        }
        if (weight.compareTo(new BigDecimal("99999")) > 0) {
            throw ApiException.badRequest("重量不能大于 99999 kg");           // ACC base.php 567
        }
        if (piece == null || piece <= 0) {
            throw ApiException.badRequest("件数必须大于零");                 // ACC base.php 569
        }
        if (piece > 999) {
            throw ApiException.badRequest("件数不能大于 999");
        }
    }

    // ─── UPS 专用规则 ───
    @SuppressWarnings("unchecked")
    private void validateUps(Map<String, Object> accCompat) {
        Map<String, Object> recv = mapOf(accCompat.get("receiver"));
        String country = strOf(accCompat.get("country"));
        if (country == null) country = strOf(recv.get("country"));

        // 1. 公司名必填（UPS_New.php 371-372）
        String company = strOf(recv.get("company"));
        if (company == null || company.isBlank()) {
            throw ApiException.badRequest("UPS 制单：收件人公司名不能为空");
        }
        if (company.length() > 35) {
            throw ApiException.badRequest("UPS 制单：收件人公司名不能超过 35 个字符（当前 "
                + company.length() + " 字符）");                              // UPS_New.php 1826
        }

        // 2. 收件人姓名 ≤ 35（UPS_New.php 1824）
        String name = strOf(recv.get("name"));
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("UPS 制单：收件人姓名不能为空");
        }
        if (name.length() > 35) {
            throw ApiException.badRequest("UPS 制单：收件人姓名不能超过 35 个字符（当前 "
                + name.length() + " 字符）");
        }

        // 3. 城市必填（UPS_New.php 373）
        String city = strOf(recv.get("city"));
        if (city == null || city.isBlank()) {
            throw ApiException.badRequest("UPS 制单：收件人城市不能为空");
        }

        // 4. 地址 ≤ 105（拼 address1 + address2，UPS_New.php 1828）
        String addr1 = strOf(recv.get("address1"));
        if (addr1 == null) addr1 = strOf(recv.get("address"));
        if (addr1 == null || addr1.isBlank()) {
            throw ApiException.badRequest("UPS 制单：收件人地址不能为空");
        }
        String addr2 = strOf(recv.get("address2"));
        int addrLen = addr1.length() + (addr2 == null ? 0 : addr2.length());
        if (addrLen > 105) {
            throw ApiException.badRequest("UPS 制单：收件人地址不能超过 105 个字符（当前 "
                + addrLen + " 字符）");
        }

        // 5. US/CA 省/州必填且在白名单（base.php 538-545）
        if ("US".equalsIgnoreCase(country) || "CA".equalsIgnoreCase(country)
            || "IE".equalsIgnoreCase(country)) {
            String province = strOf(recv.get("province"));
            if (province == null) province = strOf(recv.get("state"));
            if (province == null || province.isBlank()) {
                throw ApiException.badRequest(
                    "UPS 制单：寄往 " + country.toUpperCase() + " 必须填写省/州");
            }
            String up = province.toUpperCase();
            Set<String> whitelist = "US".equalsIgnoreCase(country) ? US_STATES
                                  : "CA".equalsIgnoreCase(country) ? CA_PROVINCES
                                  : IE_COUNTIES;
            if (!whitelist.contains(up)) {
                throw ApiException.badRequest(
                    "UPS 制单：" + country.toUpperCase() + " 的省/州代码 '" + province
                    + "' 无效（必须是二字母代码，如 CA / NY / ON）");
            }
        }

        // 6. 申报明细字段校验 + 金额自洽
        List<Map<String, Object>> declare = listOfMaps(accCompat.get("declare"));
        if (declare.isEmpty()) {
            throw ApiException.badRequest("UPS 制单：至少要提交一项申报（UPS_New.php 767）");
        }
        if (declare.size() > 100) {
            throw ApiException.badRequest("UPS 制单：申报产品种类不能超过 100 种（当前 "
                + declare.size() + "）");                                     // UPS_New.php 764
        }

        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (int i = 0; i < declare.size(); i++) {
            Map<String, Object> item = declare.get(i);
            String itemName = strOf(item.get("name"));
            String hsCode = strOf(item.get("hsCode"));
            String origin = strOf(item.get("origin"));

            if (itemName == null || itemName.isBlank()) {
                throw ApiException.badRequest(
                    "UPS 制单：第 " + (i + 1) + " 项申报英文品名不能为空");
            }
            if (itemName.length() > 105) {
                throw ApiException.badRequest(
                    "UPS 制单：第 " + (i + 1) + " 项【" + itemName + "】英文品名不能超过 105 字符"); // UPS_New.php 716
            }

            if (hsCode == null || hsCode.isBlank()) {
                throw ApiException.badRequest(
                    "UPS 制单：第 " + (i + 1) + " 项【" + itemName + "】海关编码（HSCode）不能为空"); // UPS_New.php 711
            }
            if (!HSCODE_PATTERN.matcher(hsCode).matches()) {
                throw ApiException.badRequest(
                    "UPS 制单：第 " + (i + 1) + " 项【" + itemName + "】海关编码必须为 6-15 位字母/数字/点（当前 '" + hsCode + "'）"); // UPS_New.php 714
            }

            if (origin != null && !origin.isBlank() && !COUNTRY_CODE_PATTERN.matcher(origin.toUpperCase()).matches()) {
                throw ApiException.badRequest(
                    "UPS 制单：第 " + (i + 1) + " 项【" + itemName + "】产地必须为国家二字码（如 DE / US），当前 '" + origin + "'"); // UPS_New.php 718
            }

            // 累计申报金额自洽
            BigDecimal qty = bd(item.get("quantity"));
            BigDecimal price = bd(item.get("price"));
            if (qty != null && price != null) {
                itemsTotal = itemsTotal.add(qty.multiply(price));
            }
        }

        // 7. 申报金额 = sum(items.price × qty)（base.php 556-558，允许 0.01 误差）
        BigDecimal declaredValue = bd(accCompat.get("declaredValue"));
        if (declaredValue != null && declaredValue.signum() > 0
            && itemsTotal.subtract(declaredValue).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw ApiException.badRequest(
                "UPS 制单：申报金额（" + declaredValue.setScale(2, RoundingMode.HALF_UP)
                + "）≠ 申报明细合计（" + itemsTotal.setScale(2, RoundingMode.HALF_UP)
                + "），请校对");                                              // base.php 556
        }
    }

    // ─── 辅助 ───
    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object o) {
        if (!(o instanceof List<?> l)) return List.of();
        return l.stream()
            .filter(it -> it instanceof Map<?, ?>)
            .map(it -> (Map<String, Object>) it)
            .toList();
    }

    private static String strOf(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal bd(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(o.toString()); } catch (Exception ignored) { return null; }
    }
}
