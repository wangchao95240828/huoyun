package com.xqt.saas.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xqt.saas.finance.dto.request.FinanceApprovalSaveRequest;
import com.xqt.saas.finance.entity.FinanceApprovalType;
import com.xqt.saas.finance.mapper.FinanceApprovalMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinanceApprovalService extends ServiceImpl<FinanceApprovalMapper, FinanceApprovalType> {
    private final FinanceApprovalMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public FinanceApprovalType saveOne(UUID tenantId, FinanceApprovalSaveRequest req) {
        var entity = new FinanceApprovalType();

        entity.setTenantId(tenantId);

        var now = OffsetDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        entity.setCreatedBy(req.createdBy());
        entity.setUpdatedBy(req.createdBy());

        entity.setType(req.type());
        entity.setAssociateOrderId(req.associateOrderId());
        entity.setAssociateOrderUser(req.associateOrderUser());
        entity.setReason(req.reason());
        entity.setFeeType(req.feeType());
        entity.setFeeAmount(req.feeAmount());
        entity.setModifier(req.modifier());
        entity.setBubbleModifier(req.bubbleModifier());
        entity.setApprover(req.approver());
        entity.setApplicant(req.applicant());
        entity.setApprovalReason(req.approvalReason());
        entity.setApprovalStatus(req.approvalStatus());
        entity.setPayStatus(req.payStatus());
        entity.setHappenedAt(req.happenedAt());

        mapper.insert(entity);
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteOne(@NotNull Long id, @NotNull UUID tenantId) {
        mapper.delete(new LambdaQueryWrapper<FinanceApprovalType>().
                eq(FinanceApprovalType::getId, id).
                eq(FinanceApprovalType::getTenantId, tenantId));
    }

    public List<FinanceApprovalType> listAll(UUID tenantId) {
        return mapper.selectList(new LambdaQueryWrapper<FinanceApprovalType>().
                eq(FinanceApprovalType::getTenantId, tenantId));
    }

    public IPage<FinanceApprovalType> page(UUID tenantId, Long pageNum, Long pageSize) {
        return mapper.selectPage(
                new Page<FinanceApprovalType>(pageNum, pageSize),
                new LambdaQueryWrapper<FinanceApprovalType>().
                        eq(FinanceApprovalType::getTenantId, tenantId).
                        orderByAsc(FinanceApprovalType::getCreatedAt)
        );
    }
}
