package com.example.spring.wechat.commerce.advice.service;

import com.example.spring.wechat.commerce.advice.model.ShoppingAdviceException;
import com.example.spring.wechat.commerce.advice.model.ShoppingAdviceRequest;
import com.example.spring.wechat.commerce.advice.model.ShoppingAdviceResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class ShoppingAdviceService {

    public ShoppingAdviceResult advise(ShoppingAdviceRequest rawRequest) {
        ShoppingAdviceRequest request = validate(rawRequest);
        AdviceTemplate template = templateFor(request.product());
        return new ShoppingAdviceResult(
                request.product() + "选购建议",
                budgetSummary(request.budgetMin(), request.budgetMax()),
                template.priorities(),
                contextualRecommendations(request, template),
                template.pitfalls(),
                template.checklist(),
                List.of(
                        "本建议不获取或推荐具体商品链接，不包含实时价格、库存、销量或优惠券数据",
                        "最终购买前请在正规平台核对规格、售后政策和结算价格"));
    }

    private ShoppingAdviceRequest validate(ShoppingAdviceRequest request) {
        if (request == null || request.product().isBlank()) {
            throw new ShoppingAdviceException("请告诉我想购买的商品品类");
        }
        validateBudget(request.budgetMin(), "budget_min");
        validateBudget(request.budgetMax(), "budget_max");
        if (request.budgetMin() != null && request.budgetMax() != null
                && request.budgetMin().compareTo(request.budgetMax()) > 0) {
            throw new ShoppingAdviceException("budget_min 不能大于 budget_max");
        }
        return request;
    }

    private void validateBudget(BigDecimal value, String name) {
        if (value != null && value.signum() < 0) {
            throw new ShoppingAdviceException(name + " 不能小于 0");
        }
    }

    private List<String> contextualRecommendations(ShoppingAdviceRequest request, AdviceTemplate template) {
        java.util.ArrayList<String> recommendations = new java.util.ArrayList<>(template.recommendations());
        if (!request.usage().isBlank()) {
            recommendations.add("围绕使用场景“" + request.usage() + "”确定必需功能，非核心功能不要挤占预算");
        }
        if (!request.preferences().isBlank()) {
            recommendations.add("优先满足偏好“" + request.preferences() + "”，并确认它不会明显牺牲续航、可靠性或售后");
        }
        if (!request.constraints().isBlank()) {
            recommendations.add("把限制条件“" + request.constraints() + "”列为下单前的一票否决项");
        }
        return List.copyOf(recommendations);
    }

    private String budgetSummary(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return "尚未提供预算；建议先确定可接受上限，再比较入门、均衡和高配三个档位";
        }
        if (min == null) {
            return "预算上限：¥" + money(max) + "；建议预留约 10% 用于必要配件或耗材";
        }
        if (max == null) {
            return "预算下限：¥" + money(min) + "；请补充最高可接受价格，避免推荐范围过宽";
        }
        BigDecimal reserve = max.multiply(new BigDecimal("0.10")).setScale(0, RoundingMode.HALF_UP);
        return "预算范围：¥" + money(min) + " - ¥" + money(max)
                + "；建议保留约 ¥" + money(reserve) + " 作为配件、耗材或价格波动余量";
    }

    private AdviceTemplate templateFor(String product) {
        String value = product.toLowerCase(Locale.ROOT);
        if (containsAny(value, "耳机", "蓝牙耳机", "headphone", "earphone")) {
            return new AdviceTemplate(
                    List.of("佩戴舒适度与稳固性", "主动降噪和通透模式", "实际续航与充电方式", "通话麦克风表现", "多设备切换与系统兼容性"),
                    List.of("通勤优先选择稳定连接、有效降噪和清晰通话，运动场景额外关注防水等级", "长时间佩戴时，耳型适配和单耳重量通常比音质参数更影响体验"),
                    List.of("不要只看宣传的降噪深度或编码格式", "确认低延迟、多设备连接和空间音频是否限制特定手机品牌"),
                    List.of("试戴或确认退换政策", "核对单次续航和含充电盒总续航", "检查防水等级、保修和丢失单耳补购政策"));
        }
        if (containsAny(value, "笔记本", "电脑", "laptop", "notebook")) {
            return new AdviceTemplate(
                    List.of("处理器与持续性能", "内存容量和可扩展性", "硬盘容量", "屏幕素质", "重量、续航和接口"),
                    List.of("办公学习通常优先 16GB 内存和 512GB 固态硬盘，开发、剪辑或大型数据任务应提高内存与散热优先级", "需要移动办公时，应把整机重量、电源适配器重量和实际续航一起考虑"),
                    List.of("不要只比较处理器型号而忽略散热和功耗释放", "确认内存、硬盘是否焊死以及售后拆机是否影响保修"),
                    List.of("核对屏幕分辨率、亮度和色域", "确认常用接口及外接显示器能力", "检查系统版本、保修范围和电池更换政策"));
        }
        if (containsAny(value, "充电宝", "移动电源", "power bank")) {
            return new AdviceTemplate(
                    List.of("额定容量与能量", "输出功率和快充协议", "体积重量", "接口和自带线", "安全认证"),
                    List.of("根据手机、平板或笔记本的最高充电功率选择输出规格，不必为用不到的功率支付溢价", "经常乘飞机时重点核对额定能量和航空携带规则"),
                    List.of("不要把电芯容量直接当作可用输出容量", "避免购买认证信息不清、异常低价或售后来源不明的产品"),
                    List.of("核对 CCC 等适用认证", "检查单口与多口同时输出功率", "确认额定能量、保修期和温度保护说明"));
        }
        if (containsAny(value, "空气炸锅", "烤箱", "厨房电器")) {
            return new AdviceTemplate(
                    List.of("有效容量", "温控范围和均匀性", "清洁难度", "功率与占地", "涂层和售后"),
                    List.of("按实际用餐人数选择容量，容量过大通常会增加占地和清洁负担", "高频使用时，可拆洗结构和控温稳定性比预设菜单数量更重要"),
                    List.of("不要只看标称容量，需确认炸篮或烤腔的实际可用尺寸", "谨慎对待不可拆洗、涂层说明不清和配件难购买的型号"),
                    List.of("测量台面与收纳空间", "核对插座功率和散热距离", "检查内胆材质、可拆洗部件和保修政策"));
        }
        if (containsAny(value, "露营", "帐篷", "睡袋", "camping")) {
            return new AdviceTemplate(
                    List.of("人数和使用季节", "重量与收纳体积", "防水与抗风能力", "搭建难度", "安全和耐用性"),
                    List.of("自驾露营可优先舒适和空间，徒步露营则应严格控制整套装备重量", "根据目的地温度、降雨和风力选择帐篷、睡袋及防潮方案"),
                    List.of("不要只看帐篷标称人数，需预留行李空间", "避免在不了解温标、防水指标和天气条件时购买极端轻量装备"),
                    List.of("确认搭建尺寸和营地限制", "核对防水指数、睡袋温标和地钉风绳", "出发前完整试搭并检查配件"));
        }
        return new AdviceTemplate(
                List.of("核心使用场景", "关键性能参数", "可靠性和耐用性", "使用成本", "售后和退换政策"),
                List.of("先区分必需功能、体验提升功能和低频功能，再按预算排序", "至少比较同价位三个规格档位，并重点查看与自己使用场景相符的长期体验"),
                List.of("不要只依据销量、好评率或单一参数决定", "警惕规格描述不完整、价格异常低和售后主体不明确"),
                List.of("确认尺寸、兼容性和使用环境", "核对保修、退换和耗材价格", "下单前保存规格说明并核对结算页型号"));
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String money(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private record AdviceTemplate(
            List<String> priorities,
            List<String> recommendations,
            List<String> pitfalls,
            List<String> checklist) {
    }
}
