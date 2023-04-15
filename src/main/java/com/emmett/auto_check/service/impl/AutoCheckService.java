package com.emmett.auto_check.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.emmett.auto_check.constants.Api;
import com.emmett.auto_check.domain.QueryTaskReAndResBody;
import com.emmett.auto_check.domain.RequetBody;
import com.emmett.auto_check.service.IAutoCheckService;
import com.emmett.auto_check.utils.HttpUtil;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

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

    @Value("${login.username}")
    private String username;
    @Value("${login.password}")
    private String password;

    @Override
    public void doCheck() {
        RequetBody requetBody = new RequetBody();
        String efSecurityToken = "";
        String newCookie = "";

        // 页面接口，获取token
        try {
            Response response = HttpUtil.jsonGet(LoginJspRequestUrl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 登录接口，获取cookie
        try {
            String loginUrl = String.format(Api.LoginRequestUrl, username, password);
            HashMap<String,String> loginParams = new HashMap<>();
            Response response = HttpUtil.formBodyPost(loginUrl, loginParams);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 页面接口，获取token
        try {
            Response response = HttpUtil.jsonGet(QCRT0101RequestUrl);
            List<String> cookies = response.headers("Set-Cookie");
            if (ObjectUtil.isNotEmpty(cookies)) {
                newCookie = getNewCookie(cookies);
            }
            String html = response.body().string();
            Document doc = Jsoup.parse(html);
            Element efSecurityTokenElement = doc.getElementById("efSecurityToken");
            efSecurityToken = efSecurityTokenElement.attr("value");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 任务查询接口
        List<List<String>> rows = new ArrayList<>();
        try {
            QueryTaskReAndResBody queryTaskRequetBody = new Gson().fromJson(requetBody.getQueryTaskRequetBodyString(), QueryTaskReAndResBody.class);
            queryTaskRequetBody.setEfSecurityToken(efSecurityToken);
            queryTaskRequetBody.setCOOKIE(newCookie);
            List<String> strings = queryTaskRequetBody.get__blocks__().getInqu_status().getRows().get(0);
            strings.set(2, new Date().toString());
            strings.set(3, new Date().toString());
            Response response = HttpUtil.jsonBodyPost(queryTaskRequestUrl, queryTaskRequetBody);
            assert response.body() != null;
            QueryTaskReAndResBody queryTaskResultBody = new Gson().fromJson(response.body().toString(), QueryTaskReAndResBody.class);
            rows = queryTaskResultBody.get__blocks__().getResult().getRows();
            log.info(rows.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
//
//        // 任务详情查询后处理数据提交
//        try {
//            for (List<String> row : rows) {
//                QueryTaskDetailRequetBody queryTaskDetailRequetBody = new Gson().fromJson(requetBody.getQueryTaskDetailRequetBodyString(), QueryTaskDetailRequetBody.class);
//                queryTaskDetailRequetBody.setCheckStandardId(row.get(6));
//                queryTaskDetailRequetBody.setCheckPlanInternalCode(row.get(24));
//                Response dateilResponses = HttpUtil.jsonBodyPost(queryTaskDetailRequestUrl, queryTaskDetailRequetBody, newCookie + fixedCookie);
//                List<String> cookies = dateilResponses.headers("Set-Cookie");
//                if (ObjectUtil.isNotEmpty(cookies)) {
//                    newCookie = getNewCookie(cookies);
//                }
//                log.info(newCookie);
//                assert dateilResponses.body() != null;
//                QueryTaskDetailResultBody queryTaskDetailResultBody = new Gson().fromJson(dateilResponses.body().toString(), QueryTaskDetailResultBody.class);
//                List<List<String>> detailRows = queryTaskDetailResultBody.get__blocks__().getResultXc().getRows();
//
//                /*if (rightTable[j].children[4].innerText == ""温度状态"") {
//                    leftT.getElementsByTagName(""tr"")[j].children[0].firstElementChild.click();
//                    await sleep(t);
//                    data = null;
//                    data = resultXcGrid.getCheckedRows()[0];
//                    if (data != null) {
//                        data.determinant = ""10"";
//                        data.currentResult = 22;
//                        saveB.click();
//                        i++;
//                    } else {
//                        t = (t + 5) % 30;
//                    }
//                    break;*/
//
//                CompletedRequestBody updateRequestBody = new Gson().fromJson(requetBody.getCompletedRequetBodyString(), CompletedRequestBody.class);
//                Response updateResponses = HttpUtil.jsonBodyPost(updateTaskDetailRequestUrl, updateRequestBody, newCookie + fixedCookie);
//
//                CompletedRequestBody completedRequestBody = new Gson().fromJson(requetBody.getCompletedRequetBodyString(), CompletedRequestBody.class);
//                Response completedResponses = HttpUtil.jsonBodyPost(completedRequestUrl, completedRequestBody, newCookie + fixedCookie);
//
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }

    }

    public String getNewCookie(List<String> cookies) {
        String newCookie = "";
        for (String val : cookies) {
            if (val.startsWith("JSESSIONID")) {
                int index = val.indexOf(";");
                newCookie = val.substring(0, index + 1);
                break;
            }
        }
        return newCookie;
    }

    public String getFixedCookie(List<String> cookies) {
        String fixedCookie = "";
        for (String val : cookies) {
            if (val.startsWith("iplat.sessionId")) {
                int index = val.indexOf(";");
                fixedCookie = val.substring(0, index);
                break;
            }
        }
        return fixedCookie;
    }
}
