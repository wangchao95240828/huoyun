package com.xqt.saas.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.order.common.UserContext;
import com.xqt.saas.order.dto.request.*;
import com.xqt.saas.order.dto.response.*;
import com.xqt.saas.order.entity.*;
import com.xqt.saas.order.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderWaybillService extends ServiceImpl<OrderWaybillMapper, OrderWaybill> {

    private final OrderWaybillMapper orderWaybillMapper;
    private final OrderRouteMapper orderRouteMapper;
    private final OrderContainerMapper orderContainerMapper;
    private final OrderDeclarationMapper orderDeclarationMapper;
    private final OrderAttachmentMapper orderAttachmentMapper;
    private final OrderDeliveryMapper orderDeliveryMapper;
    private final OrderWorkOrderMapper orderWorkOrderMapper;
    private final OrderPodMapper orderPodMapper;

    @Transactional(rollbackFor = Exception.class)
    public OrderWaybillView save(OrderWaybillSaveRequest request) {
        OrderWaybill entity = convertToEntity(request);
        orderWaybillMapper.insert(entity);

        Long waybillId = entity.getId();

        saveRoutes(waybillId, request.getRoutes());
        saveContainers(waybillId, request.getContainers());
        saveDeclarations(waybillId, request.getDeclarations());
        saveAttachments(waybillId, request.getAttachments());
        saveDeliveries(waybillId, request.getDeliveries());
        saveWorkOrders(waybillId, request.getWorkOrders());
        savePods(waybillId, request.getPods());

        return getById(waybillId);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderWaybillView update(OrderWaybillSaveRequest request) {
        OrderWaybill entity = orderWaybillMapper.selectById(request.getId());
        if (entity == null) {
            throw new IllegalArgumentException("运单不存在");
        }

        updateEntity(entity, request);
        orderWaybillMapper.updateById(entity);

        Long waybillId = entity.getId();

        deleteRoutes(waybillId);
        saveRoutes(waybillId, request.getRoutes());

        deleteContainers(waybillId);
        saveContainers(waybillId, request.getContainers());

        deleteDeclarations(waybillId);
        saveDeclarations(waybillId, request.getDeclarations());

        deleteAttachments(waybillId);
        saveAttachments(waybillId, request.getAttachments());

        deleteDeliveries(waybillId);
        saveDeliveries(waybillId, request.getDeliveries());

        deleteWorkOrders(waybillId);
        saveWorkOrders(waybillId, request.getWorkOrders());

        deletePods(waybillId);
        savePods(waybillId, request.getPods());

        return getById(waybillId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OrderWaybill entity = orderWaybillMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("运单不存在");
        }

        deleteRoutes(id);
        deleteContainers(id);
        deleteDeclarations(id);
        deleteAttachments(id);
        deleteDeliveries(id);
        deleteWorkOrders(id);
        deletePods(id);

        orderWaybillMapper.deleteById(id);
    }

    public OrderWaybillView getById(Long id) {
        OrderWaybill entity = orderWaybillMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("运单不存在");
        }

        OrderWaybillView view = convertToView(entity);

        view.setRoutes(getRoutesByWaybillId(id));
        view.setContainers(getContainersByWaybillId(id));
        view.setDeclarations(getDeclarationsByWaybillId(id));
        view.setAttachments(getAttachmentsByWaybillId(id));
        view.setDeliveries(getDeliveriesByWaybillId(id));
        view.setWorkOrders(getWorkOrdersByWaybillId(id));
        view.setPods(getPodsByWaybillId(id));

        return view;
    }

    public IPage<OrderWaybillView> page(OrderWaybillQueryRequest request) {
        Page<OrderWaybill> page = new Page<>(request.getPageNum(), request.getPageSize());
        LambdaQueryWrapper<OrderWaybill> queryWrapper = buildQueryWrapper(request);

        IPage<OrderWaybill> resultPage = orderWaybillMapper.selectPage(page, queryWrapper);
        return resultPage.convert(this::convertToView);
    }

    public List<OrderWaybillView> list(OrderWaybillQueryRequest request) {
        LambdaQueryWrapper<OrderWaybill> queryWrapper = buildQueryWrapper(request);
        List<OrderWaybill> list = orderWaybillMapper.selectList(queryWrapper);
        return list.stream().map(this::convertToView).collect(Collectors.toList());
    }

    private LambdaQueryWrapper<OrderWaybill> buildQueryWrapper(OrderWaybillQueryRequest request) {
        LambdaQueryWrapper<OrderWaybill> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderWaybill::getTenantId, UUID.fromString(UserContext.getTenantId()));

        if (StringUtils.hasText(request.getWaybillNo())) {
            queryWrapper.like(OrderWaybill::getWaybillNo, request.getWaybillNo());
        }
        if (request.getType() != null) {
            queryWrapper.eq(OrderWaybill::getType, request.getType());
        }
        if (StringUtils.hasText(request.getUserName())) {
            queryWrapper.like(OrderWaybill::getUserName, request.getUserName());
        }
        if (StringUtils.hasText(request.getService())) {
            queryWrapper.like(OrderWaybill::getService, request.getService());
        }
        if (StringUtils.hasText(request.getCountry())) {
            queryWrapper.like(OrderWaybill::getCountry, request.getCountry());
        }
        if (StringUtils.hasText(request.getReceiver())) {
            queryWrapper.like(OrderWaybill::getReceiver, request.getReceiver());
        }

        queryWrapper.orderByDesc(OrderWaybill::getCreateTime);
        return queryWrapper;
    }

    private OrderWaybill convertToEntity(OrderWaybillSaveRequest request) {
        OrderWaybill entity = new OrderWaybill();
        entity.setTenantId(UUID.fromString(request.getTenantId()));
        entity.setWaybillNo(request.getWaybillNo());
        entity.setType(request.getType());
        entity.setUserName(request.getUserName());
        entity.setService(request.getService());
        entity.setReceiver(request.getReceiver());
        entity.setCountry(request.getCountry());
        entity.setPieceCount(request.getPieceCount());
        entity.setBubbleRatio(request.getBubbleRatio());
        entity.setCustomerWeight(request.getCustomerWeight());
        entity.setCustomerVolume(request.getCustomerVolume());
        entity.setDeclareValue(request.getDeclareValue());
        entity.setActualWeight(request.getActualWeight());
        entity.setVolumeWeight(request.getVolumeWeight());
        entity.setVolume(request.getVolume());
        entity.setChargeWeight(request.getChargeWeight());
        entity.setCustomsMethod(request.getCustomsMethod());
        entity.setTaxMethod(request.getTaxMethod());
        entity.setCarrier(request.getCarrier());
        entity.setMainProduct(request.getMainProduct());
        entity.setProductAttr(request.getProductAttr());
        entity.setRemark(request.getRemark());
        entity.setFreightStation(request.getFreightStation());
        entity.setOrderTime(request.getOrderTime());
        entity.setCreateBy("admin");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    private void updateEntity(OrderWaybill entity, OrderWaybillSaveRequest request) {
        entity.setWaybillNo(request.getWaybillNo());
        entity.setType(request.getType());
        entity.setUserName(request.getUserName());
        entity.setService(request.getService());
        entity.setReceiver(request.getReceiver());
        entity.setCountry(request.getCountry());
        entity.setPieceCount(request.getPieceCount());
        entity.setBubbleRatio(request.getBubbleRatio());
        entity.setCustomerWeight(request.getCustomerWeight());
        entity.setCustomerVolume(request.getCustomerVolume());
        entity.setDeclareValue(request.getDeclareValue());
        entity.setActualWeight(request.getActualWeight());
        entity.setVolumeWeight(request.getVolumeWeight());
        entity.setVolume(request.getVolume());
        entity.setChargeWeight(request.getChargeWeight());
        entity.setCustomsMethod(request.getCustomsMethod());
        entity.setTaxMethod(request.getTaxMethod());
        entity.setCarrier(request.getCarrier());
        entity.setMainProduct(request.getMainProduct());
        entity.setProductAttr(request.getProductAttr());
        entity.setRemark(request.getRemark());
        entity.setFreightStation(request.getFreightStation());
        entity.setOrderTime(request.getOrderTime());
        entity.setUpdateBy("admin");
        entity.setUpdateTime(LocalDateTime.now());
    }

    private OrderWaybillView convertToView(OrderWaybill entity) {
        OrderWaybillView view = new OrderWaybillView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setWaybillNo(entity.getWaybillNo());
        view.setType(entity.getType());
        view.setTypeText(getTypeText(entity.getType()));
        view.setUserName(entity.getUserName());
        view.setService(entity.getService());
        view.setReceiver(entity.getReceiver());
        view.setCountry(entity.getCountry());
        view.setPieceCount(entity.getPieceCount());
        view.setBubbleRatio(entity.getBubbleRatio());
        view.setCustomerWeight(entity.getCustomerWeight());
        view.setCustomerVolume(entity.getCustomerVolume());
        view.setDeclareValue(entity.getDeclareValue());
        view.setActualWeight(entity.getActualWeight());
        view.setVolumeWeight(entity.getVolumeWeight());
        view.setVolume(entity.getVolume());
        view.setChargeWeight(entity.getChargeWeight());
        view.setCustomsMethod(entity.getCustomsMethod());
        view.setTaxMethod(entity.getTaxMethod());
        view.setCarrier(entity.getCarrier());
        view.setMainProduct(entity.getMainProduct());
        view.setProductAttr(entity.getProductAttr());
        view.setRemark(entity.getRemark());
        view.setFreightStation(entity.getFreightStation());
        view.setOrderTime(entity.getOrderTime());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    private void saveRoutes(Long waybillId, List<OrderRouteSaveRequest> requests) {
        if (requests == null || requests.isEmpty()) return;
        for (OrderRouteSaveRequest request : requests) {
            OrderRoute entity = new OrderRoute();
            entity.setTenantId(UUID.fromString(UserContext.getTenantId()));
            entity.setWaybillId(waybillId);
            entity.setOperationTime(request.getOperationTime());
            entity.setOperationInfo(request.getOperationInfo());
            entity.setRemark(request.getRemark());
            entity.setCommonAddress(request.getCommonAddress());
            entity.setCity(request.getCity());
            entity.setProvince(request.getProvince());
            entity.setZipCode(request.getZipCode());
            entity.setCountry(request.getCountry());
            entity.setCreateBy("admin");
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateBy("admin");
            entity.setUpdateTime(LocalDateTime.now());
            orderRouteMapper.insert(entity);
        }
    }

    private void deleteRoutes(Long waybillId) {
        LambdaQueryWrapper<OrderRoute> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderRoute::getWaybillId, waybillId);
        orderRouteMapper.delete(queryWrapper);
    }

    private List<OrderRouteView> getRoutesByWaybillId(Long waybillId) {
        LambdaQueryWrapper<OrderRoute> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderRoute::getWaybillId, waybillId);
        return orderRouteMapper.selectList(queryWrapper).stream()
                .map(this::convertRouteToView)
                .collect(Collectors.toList());
    }

    private OrderRouteView convertRouteToView(OrderRoute entity) {
        OrderRouteView view = new OrderRouteView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setWaybillId(entity.getWaybillId());
        view.setOperationTime(entity.getOperationTime());
        view.setOperationInfo(entity.getOperationInfo());
        view.setRemark(entity.getRemark());
        view.setCommonAddress(entity.getCommonAddress());
        view.setCity(entity.getCity());
        view.setProvince(entity.getProvince());
        view.setZipCode(entity.getZipCode());
        view.setCountry(entity.getCountry());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    private void saveContainers(Long waybillId, List<OrderContainerSaveRequest> requests) {
        if (requests == null || requests.isEmpty()) return;
        for (OrderContainerSaveRequest request : requests) {
            OrderContainer entity = new OrderContainer();
            entity.setTenantId(UUID.fromString(UserContext.getTenantId()));
            entity.setWaybillId(waybillId);
            entity.setContainerNo(request.getContainerNo());
            entity.setReferenceNo(request.getReferenceNo());
            entity.setCustomerData(request.getCustomerData());
            entity.setPickingData(request.getPickingData());
            entity.setPerimeter(request.getPerimeter());
            entity.setBillOfLadingNo(request.getBillOfLadingNo());
            entity.setCarrier(request.getCarrier());
            entity.setExpressMark(request.getExpressMark());
            entity.setFbaMark(request.getFbaMark());
            entity.setAbnormalFlag(request.getAbnormalFlag());
            entity.setLabelChange(request.getLabelChange());
            entity.setCheckStatus(request.getCheckStatus());
            entity.setCheckGoods(request.getCheckGoods());
            entity.setIntercept(request.getIntercept());
            entity.setFreightStation(request.getFreightStation());
            entity.setStatus(request.getStatus());
            entity.setCreateBy("admin");
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateBy("admin");
            entity.setUpdateTime(LocalDateTime.now());
            orderContainerMapper.insert(entity);
        }
    }

    private void deleteContainers(Long waybillId) {
        LambdaQueryWrapper<OrderContainer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderContainer::getWaybillId, waybillId);
        orderContainerMapper.delete(queryWrapper);
    }

    private List<OrderContainerView> getContainersByWaybillId(Long waybillId) {
        LambdaQueryWrapper<OrderContainer> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderContainer::getWaybillId, waybillId);
        return orderContainerMapper.selectList(queryWrapper).stream()
                .map(this::convertContainerToView)
                .collect(Collectors.toList());
    }

    private OrderContainerView convertContainerToView(OrderContainer entity) {
        OrderContainerView view = new OrderContainerView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setWaybillId(entity.getWaybillId());
        view.setContainerNo(entity.getContainerNo());
        view.setReferenceNo(entity.getReferenceNo());
        view.setCustomerData(entity.getCustomerData());
        view.setPickingData(entity.getPickingData());
        view.setPerimeter(entity.getPerimeter());
        view.setBillOfLadingNo(entity.getBillOfLadingNo());
        view.setCarrier(entity.getCarrier());
        view.setExpressMark(entity.getExpressMark());
        view.setFbaMark(entity.getFbaMark());
        view.setAbnormalFlag(entity.getAbnormalFlag());
        view.setLabelChange(entity.getLabelChange());
        view.setCheckStatus(entity.getCheckStatus());
        view.setCheckGoods(entity.getCheckGoods());
        view.setIntercept(entity.getIntercept());
        view.setFreightStation(entity.getFreightStation());
        view.setStatus(entity.getStatus());
        view.setStatusText(getContainerStatusText(entity.getStatus()));
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    private void saveDeclarations(Long waybillId, List<OrderDeclarationSaveRequest> requests) {
        if (requests == null || requests.isEmpty()) return;
        for (OrderDeclarationSaveRequest request : requests) {
            OrderDeclaration entity = new OrderDeclaration();
            entity.setTenantId(UUID.fromString(UserContext.getTenantId()));
            entity.setWaybillId(waybillId);
            entity.setSku(request.getSku());
            entity.setChineseName(request.getChineseName());
            entity.setEnglishName(request.getEnglishName());
            entity.setDeclarePrice(request.getDeclarePrice());
            entity.setQuantity(request.getQuantity());
            entity.setMaterial(request.getMaterial());
            entity.setPurpose(request.getPurpose());
            entity.setModel(request.getModel());
            entity.setProductWeight(request.getProductWeight());
            entity.setImageUrl(request.getImageUrl());
            entity.setCustomsCode(request.getCustomsCode());
            entity.setImage(request.getImage());
            entity.setIsElectric(request.getIsElectric());
            entity.setIsMagnetic(request.getIsMagnetic());
            entity.setProductImage(request.getProductImage());
            entity.setCreateBy("admin");
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateBy("admin");
            entity.setUpdateTime(LocalDateTime.now());
            orderDeclarationMapper.insert(entity);
        }
    }

    private void deleteDeclarations(Long waybillId) {
        LambdaQueryWrapper<OrderDeclaration> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderDeclaration::getWaybillId, waybillId);
        orderDeclarationMapper.delete(queryWrapper);
    }

    private List<OrderDeclarationView> getDeclarationsByWaybillId(Long waybillId) {
        LambdaQueryWrapper<OrderDeclaration> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderDeclaration::getWaybillId, waybillId);
        return orderDeclarationMapper.selectList(queryWrapper).stream()
                .map(this::convertDeclarationToView)
                .collect(Collectors.toList());
    }

    private OrderDeclarationView convertDeclarationToView(OrderDeclaration entity) {
        OrderDeclarationView view = new OrderDeclarationView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setWaybillId(entity.getWaybillId());
        view.setSku(entity.getSku());
        view.setChineseName(entity.getChineseName());
        view.setEnglishName(entity.getEnglishName());
        view.setDeclarePrice(entity.getDeclarePrice());
        view.setQuantity(entity.getQuantity());
        view.setMaterial(entity.getMaterial());
        view.setPurpose(entity.getPurpose());
        view.setModel(entity.getModel());
        view.setProductWeight(entity.getProductWeight());
        view.setImageUrl(entity.getImageUrl());
        view.setCustomsCode(entity.getCustomsCode());
        view.setImage(entity.getImage());
        view.setIsElectric(entity.getIsElectric());
        view.setIsMagnetic(entity.getIsMagnetic());
        view.setProductImage(entity.getProductImage());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    private void saveAttachments(Long waybillId, List<OrderAttachmentSaveRequest> requests) {
        if (requests == null || requests.isEmpty()) return;
        for (OrderAttachmentSaveRequest request : requests) {
            OrderAttachment entity = new OrderAttachment();
            entity.setTenantId(UUID.fromString(UserContext.getTenantId()));
            entity.setWaybillId(waybillId);
            entity.setAttachmentType(request.getAttachmentType());
            entity.setAttachmentUrl(request.getAttachmentUrl());
            entity.setUserVisible(request.getUserVisible());
            entity.setCreateBy("admin");
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateBy("admin");
            entity.setUpdateTime(LocalDateTime.now());
            orderAttachmentMapper.insert(entity);
        }
    }

    private void deleteAttachments(Long waybillId) {
        LambdaQueryWrapper<OrderAttachment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderAttachment::getWaybillId, waybillId);
        orderAttachmentMapper.delete(queryWrapper);
    }

    private List<OrderAttachmentView> getAttachmentsByWaybillId(Long waybillId) {
        LambdaQueryWrapper<OrderAttachment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderAttachment::getWaybillId, waybillId);
        return orderAttachmentMapper.selectList(queryWrapper).stream()
                .map(this::convertAttachmentToView)
                .collect(Collectors.toList());
    }

    private OrderAttachmentView convertAttachmentToView(OrderAttachment entity) {
        OrderAttachmentView view = new OrderAttachmentView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setWaybillId(entity.getWaybillId());
        view.setAttachmentType(entity.getAttachmentType());
        view.setAttachmentUrl(entity.getAttachmentUrl());
        view.setUserVisible(entity.getUserVisible());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    private void saveDeliveries(Long waybillId, List<OrderDeliverySaveRequest> requests) {
        if (requests == null || requests.isEmpty()) return;
        for (OrderDeliverySaveRequest request : requests) {
            OrderDelivery entity = new OrderDelivery();
            entity.setTenantId(UUID.fromString(UserContext.getTenantId()));
            entity.setWaybillId(waybillId);
            entity.setCarrierAccount(request.getCarrierAccount());
            entity.setUniqueRefNo(request.getUniqueRefNo());
            entity.setWarehouseCode(request.getWarehouseCode());
            entity.setReceiver(request.getReceiver());
            entity.setAddress1(request.getAddress1());
            entity.setAddress2(request.getAddress2());
            entity.setAddress3(request.getAddress3());
            entity.setCity(request.getCity());
            entity.setState(request.getState());
            entity.setZipCode(request.getZipCode());
            entity.setCountry(request.getCountry());
            entity.setPhone(request.getPhone());
            entity.setEmail(request.getEmail());
            entity.setBoxes(request.getBoxes());
            entity.setCreateBy("admin");
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateBy("admin");
            entity.setUpdateTime(LocalDateTime.now());
            orderDeliveryMapper.insert(entity);
        }
    }

    private void deleteDeliveries(Long waybillId) {
        LambdaQueryWrapper<OrderDelivery> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderDelivery::getWaybillId, waybillId);
        orderDeliveryMapper.delete(queryWrapper);
    }

    private List<OrderDeliveryView> getDeliveriesByWaybillId(Long waybillId) {
        LambdaQueryWrapper<OrderDelivery> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderDelivery::getWaybillId, waybillId);
        return orderDeliveryMapper.selectList(queryWrapper).stream()
                .map(this::convertDeliveryToView)
                .collect(Collectors.toList());
    }

    private OrderDeliveryView convertDeliveryToView(OrderDelivery entity) {
        OrderDeliveryView view = new OrderDeliveryView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setWaybillId(entity.getWaybillId());
        view.setCarrierAccount(entity.getCarrierAccount());
        view.setUniqueRefNo(entity.getUniqueRefNo());
        view.setWarehouseCode(entity.getWarehouseCode());
        view.setReceiver(entity.getReceiver());
        view.setAddress1(entity.getAddress1());
        view.setAddress2(entity.getAddress2());
        view.setAddress3(entity.getAddress3());
        view.setCity(entity.getCity());
        view.setState(entity.getState());
        view.setZipCode(entity.getZipCode());
        view.setCountry(entity.getCountry());
        view.setPhone(entity.getPhone());
        view.setEmail(entity.getEmail());
        view.setBoxes(entity.getBoxes());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    private void saveWorkOrders(Long waybillId, List<OrderWorkOrderSaveRequest> requests) {
        if (requests == null || requests.isEmpty()) return;
        for (OrderWorkOrderSaveRequest request : requests) {
            OrderWorkOrder entity = new OrderWorkOrder();
            entity.setTenantId(UUID.fromString(UserContext.getTenantId()));
            entity.setWaybillId(waybillId);
            entity.setTitle(request.getTitle());
            entity.setRelatedNo(request.getRelatedNo());
            entity.setDescription(request.getDescription());
            entity.setFollowers(request.getFollowers());
            entity.setResponsible(request.getResponsible());
            entity.setRemindTime(request.getRemindTime());
            entity.setUserVisible(request.getUserVisible());
            entity.setIntercept(request.getIntercept());
            entity.setCreateBy("admin");
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateBy("admin");
            entity.setUpdateTime(LocalDateTime.now());
            orderWorkOrderMapper.insert(entity);
        }
    }

    private void deleteWorkOrders(Long waybillId) {
        LambdaQueryWrapper<OrderWorkOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderWorkOrder::getWaybillId, waybillId);
        orderWorkOrderMapper.delete(queryWrapper);
    }

    private List<OrderWorkOrderView> getWorkOrdersByWaybillId(Long waybillId) {
        LambdaQueryWrapper<OrderWorkOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderWorkOrder::getWaybillId, waybillId);
        return orderWorkOrderMapper.selectList(queryWrapper).stream()
                .map(this::convertWorkOrderToView)
                .collect(Collectors.toList());
    }

    private OrderWorkOrderView convertWorkOrderToView(OrderWorkOrder entity) {
        OrderWorkOrderView view = new OrderWorkOrderView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setWaybillId(entity.getWaybillId());
        view.setTitle(entity.getTitle());
        view.setRelatedNo(entity.getRelatedNo());
        view.setDescription(entity.getDescription());
        view.setFollowers(entity.getFollowers());
        view.setResponsible(entity.getResponsible());
        view.setRemindTime(entity.getRemindTime());
        view.setUserVisible(entity.getUserVisible());
        view.setIntercept(entity.getIntercept());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    private void savePods(Long waybillId, List<OrderPodSaveRequest> requests) {
        if (requests == null || requests.isEmpty()) return;
        for (OrderPodSaveRequest request : requests) {
            OrderPod entity = new OrderPod();
            entity.setTenantId(UUID.fromString(UserContext.getTenantId()));
            entity.setWaybillId(waybillId);
            entity.setName(request.getName());
            entity.setFileName(request.getFileName());
            entity.setRemark(request.getRemark());
            entity.setCustomerVisible(request.getCustomerVisible());
            entity.setCreateBy("admin");
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateBy("admin");
            entity.setUpdateTime(LocalDateTime.now());
            orderPodMapper.insert(entity);
        }
    }

    private void deletePods(Long waybillId) {
        LambdaQueryWrapper<OrderPod> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderPod::getWaybillId, waybillId);
        orderPodMapper.delete(queryWrapper);
    }

    private List<OrderPodView> getPodsByWaybillId(Long waybillId) {
        LambdaQueryWrapper<OrderPod> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderPod::getWaybillId, waybillId);
        return orderPodMapper.selectList(queryWrapper).stream()
                .map(this::convertPodToView)
                .collect(Collectors.toList());
    }

    private OrderPodView convertPodToView(OrderPod entity) {
        OrderPodView view = new OrderPodView();
        view.setId(entity.getId());
        view.setTenantId(entity.getTenantId());
        view.setWaybillId(entity.getWaybillId());
        view.setName(entity.getName());
        view.setFileName(entity.getFileName());
        view.setRemark(entity.getRemark());
        view.setCustomerVisible(entity.getCustomerVisible());
        view.setCreateBy(entity.getCreateBy());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateBy(entity.getUpdateBy());
        view.setUpdateTime(entity.getUpdateTime());
        return view;
    }

    public static String getTypeText(Integer type) {
        if (type == null) return "";
        switch (type) {
            case 1: return "已下单";
            case 2: return "已收货";
            case 3: return "转运中";
            case 4: return "已签收";
            case 5: return "退件";
            case 6: return "已取消";
            default: return "";
        }
    }

    public static String getContainerStatusText(Integer status) {
        if (status == null) return "";
        switch (status) {
            case 1: return "已下单";
            case 2: return "未下单";
            default: return "";
        }
    }
}
