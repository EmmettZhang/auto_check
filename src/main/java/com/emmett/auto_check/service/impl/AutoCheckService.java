package com.emmett.auto_check.service.impl;

import com.emmett.auto_check.config.SYSConfig;
import com.emmett.auto_check.constants.Api;
import com.emmett.auto_check.domain.*;
import com.emmett.auto_check.service.IAutoCheckService;
import com.emmett.auto_check.utils.HttpUtil;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.springframework.stereotype.Service;

import javax.swing.*;
import java.util.*;

import static com.emmett.auto_check.constants.Api.*;

/**
 * Description: 申请开票
 *
 * @author tpf
 * @since 2022-08-06
 */
@Slf4j
@Service
public class AutoCheckService implements IAutoCheckService {

    private final RequestBody requetBody = new RequestBody();

    @Override
    public void doCheck(SYSConfig.User user, SYSConfig sysConfig) {

        // 页面接口，获取token
        try {
            Response response = HttpUtil.jsonGet(LoginJspRequestUrl);
            response.close();
        } catch (Exception e) {
            log.error(e.getMessage());
            JOptionPane.showMessageDialog(null, e.getMessage() +
                    "\n\n如不能自行解决，不要关闭此窗口，截图联系yin_zjl@foxmail.com", "别动！", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(e);
        }

        // 登录接口，获取cookie
        try {
            String loginUrl = String.format(Api.LoginRequestUrl, user.getId(), user.getPassword());
            HashMap<String,String> loginParams = new HashMap<>();
            Response response = HttpUtil.formBodyPost(loginUrl, loginParams);
            response.close();
        } catch (Exception e) {
            log.error(e.getMessage());
            JOptionPane.showMessageDialog(null, e.getMessage() +
                    "\n\n如不能自行解决，不要关闭此窗口，截图联系yin_zjl@foxmail.com", "别动！", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(e);
        }

        // 页面接口，获取token
        String efSecurityToken;
        try {
            Response response = HttpUtil.jsonGet(QCRT0101RequestUrl);
            assert response.body() != null;
            String html = response.body().string();
            Document doc = Jsoup.parse(html);
            Element efSecurityTokenElement = doc.getElementById("efSecurityToken");
            assert efSecurityTokenElement != null;
            efSecurityToken = efSecurityTokenElement.attr("value");
            response.close();
        } catch (Exception e) {
            log.error(e.getMessage());
            JOptionPane.showMessageDialog(null, e.getMessage() +
                    "\n\n如不能自行解决，不要关闭此窗口，截图联系yin_zjl@foxmail.com", "别动！", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(e);
        }
        // 任务查询接口
        List<List<String>> rows;
        try {
            QueryTaskReAndResBody queryTaskRequestBody = new Gson().fromJson(requetBody.getQueryTaskRequetBodyString(), QueryTaskReAndResBody.class);
            queryTaskRequestBody.setEfSecurityToken(efSecurityToken);
            queryTaskRequestBody.setCOOKIE(MyCookieJar.getFixCookieValue());
            List<String> strings = queryTaskRequestBody.get__blocks__().getInqu_status().getRows().get(0);
            strings.set(2, sysConfig.getBeginTime());
            strings.set(3, sysConfig.getEndTime());
            queryTaskRequestBody.get__blocks__().getResult().getAttr().setLimit(sysConfig.getLimit());
            Response response = HttpUtil.jsonBodyPost(queryTaskRequestUrl, queryTaskRequestBody, efSecurityToken);
            assert response.body() != null;
            QueryTaskReAndResBody queryTaskResultBody = new Gson().fromJson(response.body().string(), QueryTaskReAndResBody.class);
            rows = queryTaskResultBody.get__blocks__().getResult().getRows();
            response.close();
        } catch (Exception e) {
            log.error(e.getMessage());
            JOptionPane.showMessageDialog(null, e.getMessage() +
                    "\n\n如不能自行解决，不要关闭此窗口，截图联系yin_zjl@foxmail.com", "别动！", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(e);
        }

        if (rows.size() == 0) {
            log.error("[*" + user.getName() + "*]：" + sysConfig.getBeginTime() + "-" + sysConfig.getEndTime() + "无点检或已点检");
            return;
        }

        // 任务详情查询后处理数据提交
        try {
            CompletedRequestBody completedRequestBody = new Gson().fromJson(requetBody.getCompletedRequetBodyString(), CompletedRequestBody.class);
            //TODO:异步处理更新
            update(rows, completedRequestBody, sysConfig);
            Response completedResponses = HttpUtil.jsonBodyPost(completedRequestUrl, completedRequestBody, efSecurityToken);
            assert completedResponses.body() != null;
            CompleteResultBody completeResultBody = new Gson().fromJson(completedResponses.body().string(), CompleteResultBody.class);
            completedResponses.close();
            if (completeResultBody.get__sys__().getStatus() == 0) {
                log.error("[*" + user.getName() + "*]：" + sysConfig.getBeginTime() + "-" + sysConfig.getEndTime() + "点检数：" + rows.size());
            } else {
                log.error("[*" + user.getName() + "*]：" + sysConfig.getBeginTime() + "-" + sysConfig.getEndTime() + "点检提交失败：" + completeResultBody.get__sys__().getMsg());
                log.error("[*" + user.getName() + "*]：" + sysConfig.getBeginTime() + "-" + sysConfig.getEndTime() + "失败详情：" + completeResultBody.get__sys__().getDetailMsg());
                JOptionPane.showMessageDialog(null, "长辈解锁成就：探索者；同龄人解锁成就：笨蛋\n\n"
                        + user.getName() + "点检提交失败!\n" + completeResultBody.get__sys__().getMsg()
                        + "\n************热心建议************：检查配置干嘛，愣着啊\n"
                        + "如不能自行解决，不要关闭此窗口，截图联系yin_zjl@foxmail.com", "恭喜！", JOptionPane.QUESTION_MESSAGE);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            JOptionPane.showMessageDialog(null, e.getMessage() +
                    "\n\n如不能自行解决，不要关闭此窗口，截图联系yin_zjl@foxmail.com", "别动！", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(e);
        }

    }

    private void update(List<List<String>> rows, CompletedRequestBody completedRequestBody, SYSConfig sysConfig) {

        try {

            List<List<String>> resultRows = completedRequestBody.get__blocks__().getResult().getRows();

            QueryTaskDetailRequetBody queryTaskDetailRequetBody = new Gson().fromJson(requetBody.getQueryTaskDetailRequetBodyString(), QueryTaskDetailRequetBody.class);

            for (List<String> row : rows) {
                queryTaskDetailRequetBody.setCheckStandardId(row.get(6));
                queryTaskDetailRequetBody.setCheckPlanInternalCode(row.get(24));
                Response detailResponses = HttpUtil.jsonBodyPost(queryTaskDetailRequestUrl, queryTaskDetailRequetBody, "");
                assert detailResponses.body() != null;
                QueryTaskDetailResultBody queryTaskDetailResultBody = new Gson().fromJson(detailResponses.body().string(), QueryTaskDetailResultBody.class);
                List<List<String>> detailRows = queryTaskDetailResultBody.get__blocks__().getResultXc().getRows();
                List<String> dataRow = new ArrayList<>(), temp;
                //13,19,4,3,5,6,7(数值),9（10判定正常）,10,11,8,12,15,16,14,18
                List<List<String>> detailRowsSpe = detailRows.stream().filter(item -> sysConfig.getOpData().contains(item.get(6))).toList();
                for (List<String> detailRow: detailRowsSpe) {
                    detailRow.set(7, sysConfig.getOpValue());
                    detailRow.set(9, "10");
                    UpdateTaskDetailRequestBody updateTaskDetailRequestBody = new Gson().fromJson(requetBody.getUpdateTaskDetailRequetBodyString(), UpdateTaskDetailRequestBody.class);
                    temp = updateTaskDetailRequestBody.get__blocks__().getInqu_status().getRows().get(0);
                    temp.set(0, detailRow.get(3));
                    temp.set(2, sysConfig.getBeginTime());
                    temp.set(3, sysConfig.getEndTime());
                    dataRow.clear();
                    dataRow.add(detailRow.get(13));
                    dataRow.add(detailRow.get(19));
                    dataRow.add(detailRow.get(4));
                    dataRow.add(detailRow.get(3));
                    dataRow.add(detailRow.get(5));
                    dataRow.add(detailRow.get(6));
                    dataRow.add(detailRow.get(7));
                    dataRow.add(detailRow.get(9));
                    dataRow.add(detailRow.get(10));
                    dataRow.add(detailRow.get(11));
                    dataRow.add(detailRow.get(8));
                    dataRow.add(detailRow.get(12));
                    dataRow.add(detailRow.get(15));
                    dataRow.add(detailRow.get(16));
                    dataRow.add(detailRow.get(14));
                    dataRow.add(detailRow.get(18));
                    updateTaskDetailRequestBody.get__blocks__().getResultXc().getRows().set(0, dataRow);
                    Response response = HttpUtil.jsonBodyPost(updateTaskDetailRequestUrl, updateTaskDetailRequestBody, "");
                    response.close();
                }

                complete(resultRows, row);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            JOptionPane.showMessageDialog(null, e.getMessage() +
                    "\n\n如不能自行解决，不要关闭此窗口，截图联系yin_zjl@foxmail.com", "别动！", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(e);
        }

    }

    public void complete(List<List<String>> resultRows, List<String> row) {
        List<String> resultRow = new ArrayList<>();
        resultRow.add(row.get(24));
        resultRow.add(row.get(6));
        resultRow.add("0");
        resultRow.add(row.get(8));
        resultRow.add("1");
        resultRow.add(row.get(5));
        resultRow.add(row.get(13));
        resultRow.add(row.get(2));
        resultRow.add(row.get(13));
        resultRows.add(resultRow);
    }

}
