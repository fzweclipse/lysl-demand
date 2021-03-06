package com.whu.lysl.service.donation.impl;

import com.whu.lysl.base.converters.DonationOrderConverter;
import com.whu.lysl.base.converters.MaterialOrderConverter;
import com.whu.lysl.base.enums.OrderStatusEnum;
import com.whu.lysl.base.enums.DonationTypeEnum;
import com.whu.lysl.base.enums.LYSLResultCodeEnum;
import com.whu.lysl.base.enums.LovePoolStatusEnum;
import com.whu.lysl.base.exceptions.LYSLException;
import com.whu.lysl.base.utils.AssertUtils;
import com.whu.lysl.base.utils.StringUtils;
import com.whu.lysl.dao.DonationOrderDAO;
import com.whu.lysl.entity.condition.DonationOrderCondition;
import com.whu.lysl.entity.condition.InstCondition;
import com.whu.lysl.entity.condition.MaterialOrderCondition;
import com.whu.lysl.entity.dbobj.DonationOrderDO;
import com.whu.lysl.entity.dto.DonationOrder;
import com.whu.lysl.entity.dto.Institution;
import com.whu.lysl.entity.dto.MaterialOrder;
import com.whu.lysl.service.donation.DonationOrderService;
import com.whu.lysl.service.institution.InstitutionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * @Author Caspar
 * @CreateTime 2020/2/9 15:24
 * @Description:
 */
@Service
@Slf4j
public class DonationOrderServiceImpl implements DonationOrderService {
    @Resource
    private DonationOrderDAO donationOrderDAO;
    @Resource
    private InstitutionService institutionService;

    @Override
    public List<DonationOrder> getDonationOrderByCondition(DonationOrderCondition donationOrderCondition) {
        List<DonationOrder> donationOrderList = DonationOrderConverter.batchDo2Model(donationOrderDAO.selectByCondition(donationOrderCondition));
        if (donationOrderList!=null) {
            for (DonationOrder donationOrder : donationOrderList) {
                donationOrder.setMaterialOrderList(getMaterialOrderByDonationOrderId(donationOrder.getDonationOrderId()));
            }
        }
        return donationOrderList;
    }

    @Override
    public List<MaterialOrder> getMaterialOrderByDonationOrderId(Integer donationOrderId) {
        AssertUtils.AssertNotNull(donationOrderId, "donationOrderId is null");
        return MaterialOrderConverter.batchDo2Model(donationOrderDAO.selectMaterialOrderByCondition(
                new MaterialOrderCondition.Builder().donationOrderId(donationOrderId).build()));
    }

    @Override
    public List<DonationOrder> getDonationOrderByDonorId(Integer donorId) {
        AssertUtils.AssertNotNull(donorId, "donorId is null");
        return getDonationOrderByCondition(new DonationOrderCondition.Builder().donorId(donorId).build());
    }

    @Override
    public List<DonationOrder> getDonationOrderByStatus(String status) {
        AssertUtils.AssertNotNull(status, "status is null");
        if (!EnumUtils.isValidEnum(OrderStatusEnum.class, status)) {
            throw new LYSLException("Status 不属于支持的枚举值", LYSLResultCodeEnum.DATA_INVALID);
        }
        return getDonationOrderByCondition(new DonationOrderCondition.Builder().status(status).build());
    }

    @Override
    public List<DonationOrder> getDonationOrderByStatusAndDonationType(String status, String donationType) {
        AssertUtils.AssertNotNull(status);
        AssertUtils.AssertNotNull(donationType);
        if (!EnumUtils.isValidEnum(OrderStatusEnum.class, status)) {
            throw new LYSLException("status 不属于支持的枚举值", LYSLResultCodeEnum.DATA_INVALID);
        }
        if (!EnumUtils.isValidEnum(DonationTypeEnum.class, donationType)) {
            throw new LYSLException("donationType 不属于支持的枚举值", LYSLResultCodeEnum.DATA_INVALID);
        }
        return getDonationOrderByCondition(
                        new DonationOrderCondition.Builder().status(status).donationType(donationType).build());
    }

    @Override
    public List<DonationOrder> getDonationOrderInLovePool(String lovePoolStatus) {
        AssertUtils.AssertNotNull(lovePoolStatus, "donationOrderId is null");
        if (!EnumUtils.isValidEnum(LovePoolStatusEnum.class, lovePoolStatus)) {
            throw new LYSLException("lovePoolStatus 不属于支持的枚举值", LYSLResultCodeEnum.DATA_INVALID);
        }
        return getDonationOrderByCondition(
                        new DonationOrderCondition.Builder().lovePoolStatus(lovePoolStatus).build());
    }

    @Override
    public int updateDonationOrderLovePoolStatus(DonationOrder donationOrder, String lovePoolStatus) {
        donationOrder.setLovePoolStatus(lovePoolStatus);  // 这边是为了下面对 donationOrder 进行校验
        validateInsertDonatiionOrder(donationOrder);
        DonationOrder donationOrder1 = new DonationOrder();
        donationOrder1.setLovePoolStatus(donationOrder.getLovePoolStatus());
        donationOrder1.setDonationOrderId(donationOrder.getDonationOrderId());
        return updateDonationOrder(donationOrder1);
    }

    @Override
    public Boolean validateDonationOrderId(Integer donationOrderId) {
        if (donationOrderId!=null) {
            List<DonationOrderDO> donationOrderList = donationOrderDAO.selectByCondition(
                    new DonationOrderCondition.Builder().donationOrderId(donationOrderId).build());
            if (donationOrderList!=null && donationOrderList.size()>=1) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    @Override
    public int insertDonationOrder(DonationOrder donationOrder) {
        donationOrder.setStatus(OrderStatusEnum.UNCHECKED.getCode());
        donationOrder.setLovePoolStatus(LovePoolStatusEnum.NOT_IN_POOL.getCode());
        donationOrder.setDeleted(0);
        validateInsertDonatiionOrder(donationOrder);
        DonationOrderDO donationOrderDO = DonationOrderConverter.model2Do(donationOrder);
        donationOrderDAO.insertDonationOrder(donationOrderDO);
        return donationOrderDO.getDonationOrderId();
    }

//    @Override
//    public int insertDonationOrderGetId(DonationOrder donationOrder) {
//        donationOrder.setStatus(DonationOrderStatusEnum.UNCHECKED.getCode());
//        donationOrder.setLovePoolStatus(LovePoolStatusEnum.NOT_IN_POOL.getCode());
//        donationOrder.setDeleted(0);
//        validateInsertDonatiionOrder(donationOrder);
//        DonationOrderDO donationOrderDO = DonationOrderConverter.model2Do(donationOrder);
//        donationOrderDAO.insertDonationOrder(donationOrderDO);
//        return donationOrderDO.getDonationOrderId();
//    }

    @Transactional(rollbackFor = LYSLException.class)
    @Override
    public int insertDonationOrderDetail(DonationOrder donationOrder) {
        log.info("开始新增一条捐赠：" + donationOrder);

        Integer donationOrderId = insertDonationOrder(donationOrder);
        if (donationOrderId==null || donationOrderId<=0) {
            throw new LYSLException("插入捐赠清单失败" ,LYSLResultCodeEnum.SYSTEM_ERROR);
        }
        for (MaterialOrder materialOrder: donationOrder.getMaterialOrderList()) {
            materialOrder.setDonationOrderId(donationOrderId);
            int ins_ans = insertMaterialOrder(materialOrder);
            if (ins_ans!=1) {
                throw new LYSLException("插入物资清单失败:"+materialOrder.toString() ,LYSLResultCodeEnum.SYSTEM_ERROR);
            }
        }

        log.info("成功新增一条捐赠：" + donationOrder);
        return donationOrderId;
    }

    @Override
    public int insertMaterialOrder(MaterialOrder materialOrder) {
        return donationOrderDAO.insertMaterialOrder(MaterialOrderConverter.model2Do(materialOrder));
    }

    @Override
    public int updateDonationOrder(DonationOrder donationOrder) {
        AssertUtils.AssertNotNull(donationOrder);
        return donationOrderDAO.updateDonationOrder(DonationOrderConverter.model2Do(donationOrder));
    }

//    public int checkPass(DonationOrder donationOrder) {
//        validateInsertDonatiionOrder(donationOrder);
//        DonationOrder donationOrder1 = new DonationOrder();
//        donationOrder1.setStatus(DonationOrderStatusEnum.APPROVED.getCode());
//        donationOrder1.setDonationOrderId(donationOrder.getDonationOrderId());
//        return updateDonationOrder(donationOrder1);
//    }
//
//    public int checkFail(DonationOrder donationOrder) {
//        validateInsertDonatiionOrder(donationOrder);
//        DonationOrder donationOrder1 = new DonationOrder();
//        donationOrder1.setStatus(DonationOrderStatusEnum.DISAPPROVED.getCode());
//        donationOrder1.setDonationOrderId(donationOrder.getDonationOrderId());
//        return updateDonationOrder(donationOrder1);
//    }

    public int check(DonationOrder donationOrder, String status) {
        donationOrder.setStatus(status);  // 这边是为了下面对 donationOrder 进行校验
        validateInsertDonatiionOrder(donationOrder);
        DonationOrder donationOrder1 = new DonationOrder();
        donationOrder1.setStatus(donationOrder.getStatus());
        // 如果审核通过且为非定向捐赠，则加入爱心池
        if (donationOrder.getStatus().equals(OrderStatusEnum.APPROVED.getCode())
                && donationOrder.getDonationType().equals(DonationTypeEnum.UNDIRECTED.getCode())) {
            donationOrder1.setLovePoolStatus(LovePoolStatusEnum.IN_POOL.getCode());
        }
        donationOrder1.setDonationOrderId(donationOrder.getDonationOrderId());
        return updateDonationOrder(donationOrder1);
    }

    public int changeLovePoolStatus(DonationOrder donationOrder, String lovePoolStatus) {
        donationOrder.setLovePoolStatus(lovePoolStatus);
        validateInsertDonatiionOrder(donationOrder);
        DonationOrder donationOrder1 = new DonationOrder();
        donationOrder1.setStatus(donationOrder.getStatus());
        donationOrder1.setDonationOrderId(donationOrder.getDonationOrderId());
        return updateDonationOrder(donationOrder1);
    }

    public void validateInsertDonatiionOrder(DonationOrder donationOrder) {
        AssertUtils.AssertNotNull(donationOrder, "donationOrder is null");
        AssertUtils.AssertNotNull(donationOrder.getMaterialOrderList(), "materialOrderList is null");

        if (!EnumUtils.isValidEnum(DonationTypeEnum.class, donationOrder.getDonationType())) {
            throw new LYSLException("donationType 不属于支持的枚举值 {DIRECTED, UNDIRECTED}", LYSLResultCodeEnum.DATA_INVALID);
        }
        if (!EnumUtils.isValidEnum(OrderStatusEnum.class, donationOrder.getStatus())) {
            throw new LYSLException("donationStatus 不属于支持的枚举值 {UNCHECKED, APPROVED, DISAPPROVED}", LYSLResultCodeEnum.DATA_INVALID);
        }
        if (!EnumUtils.isValidEnum(LovePoolStatusEnum.class, donationOrder.getLovePoolStatus())) {
            throw new LYSLException("love_pool_status 不属于支持的枚举值 {NOT_IN_POOL, IN_POOL, ARTI_DISPATCHED, AUTO_DISPATCHED}", LYSLResultCodeEnum.DATA_INVALID);
        }
//        定向捐赠 donorId 非空， 非定向捐赠 donorId=-1
        if (StringUtils.equal(donationOrder.getDonationType(), DonationTypeEnum.DIRECTED.getCode())) {
            validateIns(donationOrder.getDoneeId(), donationOrder.getDoneeName());
        } else {
            donationOrder.setDoneeId(-1);
        }
        //TODO 校验 materialId 和 materialName 有效性

    }

    @Override
    public int deleteDonationOrder(Integer donationOrderId) {
        AssertUtils.AssertNotNull(donationOrderId, "donationOrderId is null");
        return donationOrderDAO.deleteDonationOrder(donationOrderId);
    }

    private void validateIns(Integer instId, String isntName) {
        List<Institution> institutionList = institutionService.getInstsByCondition(
                new InstCondition.Builder().id(instId).name(isntName).build());

        if (institutionList == null || institutionList.isEmpty()) {
            throw new LYSLException("doneeId and doneeName do not match!", LYSLResultCodeEnum.DATA_INVALID);
        } else if (!StringUtils.equal(institutionList.get(0).getStatus()
                , OrderStatusEnum.APPROVED.getCode())) {
            throw new LYSLException("The institution of this donee has not been checked!"
                    , LYSLResultCodeEnum.DATA_INVALID);
        }
    }

}
