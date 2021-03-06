package com.whu.lysl.web.controllers;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.whu.lysl.base.enums.OrderStatusEnum;
import com.whu.lysl.base.enums.LYSLResultCodeEnum;
import com.whu.lysl.base.exceptions.LYSLException;
import com.whu.lysl.base.utils.StringUtils;
import com.whu.lysl.entity.condition.DemandCondition;
import com.whu.lysl.entity.condition.InstCondition;
import com.whu.lysl.entity.dbobj.DemandDO;
import com.whu.lysl.entity.dto.Institution;
import com.whu.lysl.entity.dto.User;
import com.whu.lysl.entity.vo.DemandVO;
import com.whu.lysl.service.demand.DemandService;
import com.whu.lysl.service.institution.InstitutionService;
import com.whu.lysl.service.user.UserService;
import com.whu.lysl.web.LYSLBaseController;
import com.whu.lysl.web.LYSLResult;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 机构 controller
 * @author Powen
 */
@RestController
@RequestMapping("demand")
public class DemandController extends LYSLBaseController {

    /** 需求服务 */
    @Resource
    private DemandService demandService;

    /** 机构服务 */
    @Resource
    private InstitutionService institutionService;

    /** 用户服务 */
    @Resource
    private UserService userService;

    private class DemandOrderVO {
        // 必须是 public 否则 JSON 无法序列化
        public int demandId;
        public String insitituionName;
        public Date time;
        public String description;
        public List<Map<String, String>> materials = new ArrayList<>();
        public String address;
        public String contactName;
        public String contactPhone;
    }

    @RequestMapping("getUnreviewedDemands")
    public String getUnreviewedDemands(HttpServletRequest request) {
        LYSLResult<Object> res = protectController(request, () -> {
            LYSLResult<Object> result = new LYSLResult<>();
            List<DemandVO> demandList = demandService.getUnreviewedDemands();

            result.setCount(demandList.size());
            result.setResultObj(demandList);
            return result;
        }, BaseControllerEnum.IGNORE_VERIFY.getCode(), BaseControllerEnum.BACK_MANAGE.getCode());

        return JSON.toJSONString(res, SerializerFeature.DisableCircularReferenceDetect);
    }

    @RequestMapping("getUnreviewedDemandsById")
    public String getUnreviewedDemandsById(@RequestBody String jsonString, HttpServletRequest request) {
        LYSLResult<Object> res = protectController(request, () -> {
            LYSLResult<Object> result = new LYSLResult<>();
            List<DemandVO> demandList = demandService.
                    getUnreviewedDemandsById(jsonString);
            result.setResultObj(demandList);
            return result;
        }, BaseControllerEnum.IGNORE_VERIFY.getCode());

        return JSON.toJSONString(res);
    }

    @RequestMapping("insertDemand")
    public String insertDemand(@RequestBody String jsonString, HttpServletRequest request) {
        LYSLResult<Object> res = protectController(request, () -> {
            LYSLResult<Object> result = new LYSLResult<>();
            demandService.insertDemand(jsonString);
            result.setResultObj("插入成功");
            return result;
        }, BaseControllerEnum.IGNORE_VERIFY.getCode());

        return JSON.toJSONString(res);
    }

    @GetMapping("verifyDemand")
    public String modifyStatus(HttpServletRequest request) {
        LYSLResult<Object> res = protectController(request, () -> {
            LYSLResult<Object> result = new LYSLResult<>();

            String demandId = request.getParameter("demandId");
            String verify = request.getParameter("verify");

            List<DemandDO> demands = demandService.getDemandsByCondition(new DemandCondition.Builder()
                    .demandId(demandId).status(OrderStatusEnum.UNCHECKED.getCode()).build());
            if (demands.size() == 0) {
                throw new LYSLException("审核的订单不存在或已审核过", LYSLResultCodeEnum.DATA_INVALID);
            }

            if (StringUtils.equal(verify, "APPROVE")) {
                demandService.modifyDemandOrderStatus(demandId, OrderStatusEnum.APPROVED);
            } else if (StringUtils.equal(verify, "DISAPPROVE")) {
                demandService.modifyDemandOrderStatus(demandId, OrderStatusEnum.DISAPPROVED);
            } else {
                throw new LYSLException("错误的审核动作", LYSLResultCodeEnum.DATA_INVALID);
            }

            return result;
        }, BaseControllerEnum.IGNORE_VERIFY.getCode(), BaseControllerEnum.BACK_MANAGE.getCode());
        // todo 鉴权
        return JSON.toJSONString(res);
    }

    @GetMapping("getDemandList")
    public String getDemandList(HttpServletRequest request) {
        LYSLResult<Object> res = protectController(request, () -> {

            LYSLResult<Object> result = new LYSLResult<>();

            int pageNo = Integer.parseInt(request.getParameter("pageNo"));
            int pageSize = Integer.parseInt(request.getParameter("pageSize"));

            String province = request.getParameter("province");
            String city = request.getParameter("city");
            List<Institution> institutions = institutionService.getInstsByCondition(new InstCondition.Builder()
                    .province(province).city(city).status(OrderStatusEnum.APPROVED.getCode()).build());

            List<DemandOrderVO> demandOrderVOS = generateDemandOrderVOListByInsts(institutions);
            Collections.sort(demandOrderVOS, (DemandOrderVO o1, DemandOrderVO o2) -> {
                if (o1.time.getTime() >= o2.time.getTime()) {
                    return 1;
                }
                return -1;
            });

            int fromIndex = (pageNo * pageSize) > demandOrderVOS.size() ?
                    demandOrderVOS.size() : (pageNo * pageSize);
            int toIndex = (pageSize * (pageNo + 1)) > demandOrderVOS.size() ?
                    demandOrderVOS.size() : (pageSize * (pageNo + 1));
            List<DemandOrderVO> resList = demandOrderVOS.subList(fromIndex, toIndex);

            result.setResultObj(resList);
            return result;
        }, BaseControllerEnum.IGNORE_VERIFY.getCode());

        return JSON.toJSONString(res, SerializerFeature.DisableCircularReferenceDetect);
    }

    /**
     * 从机构 list 生成 需求单 list
     * @param institutions 机构 list
     * @return 需求单 list
     */
    private List<DemandOrderVO> generateDemandOrderVOListByInsts(List<Institution> institutions) {
        List<DemandOrderVO> demandOrderVOS = new ArrayList<>();
        for (Institution institution : institutions) {
            List<DemandDO> demandDOS = demandService.getDemandsByCondition(new DemandCondition.Builder()
                    .institutionId(institution.getId()).build());
            if (demandDOS.size() > 0) {
                User user = userService.getUserById(demandDOS.get(0).getDoneeId());
                demandOrderVOS.add(generateDemandOrderVO(demandDOS, institution, user));
            }
        }

        return demandOrderVOS;
    }

    private DemandOrderVO generateDemandOrderVO(List<DemandDO> demandDOS, Institution institution, User user) {
        DemandOrderVO demandOrderVO = new DemandOrderVO();

        if (institution != null) {
            demandOrderVO.insitituionName = institution.getName();
            demandOrderVO.time = institution.getGmtModified();
            demandOrderVO.address = institution.getProvince() + " " + institution.getCity() + " " + institution.getDistrict()
                    + " " + institution.getAddress();
        }

        for (DemandDO demandDO : demandDOS) {
            Map<String, String> map = new HashMap<>();
            map.put("materialName", demandDO.getMaterialName());
            map.put("materialNum", demandDO.getMaterialNum() + "");
            demandOrderVO.materials.add(map);
            demandOrderVO.demandId =  Integer.parseInt(demandDO.getDemandId());
            demandOrderVO.description = demandDO.getDescription();
        }

        if (user != null) {
            demandOrderVO.contactName = user.getName();
            demandOrderVO.contactPhone = user.getPhone();
        }

        return demandOrderVO;
    }

    @GetMapping("getDemandDetailById")
    public String getDemandDetailById(HttpServletRequest request, @RequestParam("demandId") String demandId) {
        LYSLResult<Object> res = protectController(request, () -> {
            LYSLResult<Object> result = new LYSLResult<>();

            List<DemandDO> demandDOS = demandService.getDemandsByCondition(new DemandCondition.Builder()
                    .demandId(demandId).build());
            if (demandDOS.size() == 0) {
                throw new LYSLException("该需求单不存在", LYSLResultCodeEnum.DATA_INVALID);
            }
            Institution institution = institutionService.getInstsByCondition(new InstCondition.Builder().
                    id(demandDOS.get(0).getInstitutionId()).build()).get(0);
            User user = userService.getUserById(demandDOS.get(0).getDoneeId());
            DemandOrderVO demandOrderVO = generateDemandOrderVO(demandDOS, institution, user);

            result.setResultObj(demandOrderVO);
            return result;
        }, BaseControllerEnum.IGNORE_VERIFY.getCode());

        return JSON.toJSONString(res);
    }

}
