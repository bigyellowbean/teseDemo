////
//// Source code recreated from a .class file by IntelliJ IDEA
//// (powered by Fernflower decompiler)
////
//
//package com.zjft.zhyg.relay.core;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.zjft.zhyg.relay.callback.RequestCallback;
//import com.zjft.zhyg.relay.dao.DeviceDao;
//import com.zjft.zhyg.relay.loadbalancer.ZhygLoadBalancer;
//import com.zjft.zhyg.relay.pojo.ServerManager;
//import com.zjft.zhyg.relay.properties.RequestThreadPoolProperties;
//import com.zjft.zhyg.subject.pojo.AsyncRsp;
//import java.io.IOException;
//import java.util.Collections;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.LinkedBlockingDeque;
//import java.util.concurrent.ThreadPoolExecutor;
//import java.util.concurrent.TimeUnit;
//import java.util.function.BiConsumer;
//import javax.annotation.PostConstruct;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.http.HttpEntity;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
//import org.springframework.util.Assert;
//import org.springframework.util.CollectionUtils;
//import org.springframework.web.client.RestClientException;
//import org.springframework.web.client.RestTemplate;
//
//public class RequestRelay {
//    private static final Logger log = LoggerFactory.getLogger(RequestRelay.class);
//    @Autowired
//    @Qualifier("zhygLbRestTemplate")
//    private RestTemplate restTemplate;
//    @Autowired
//    private DeviceDao deviceDao;
//    @Autowired
//    private ZhygLoadBalancer zhygLoadBalancer;
//    @Autowired
//    private RequestThreadPoolProperties threadPoolProperties;
//    private ThreadPoolExecutor threadPoolExecutor;
//    private static final String COL_COMPANY = "COMPANY";
//    private static final String COL_ORG_NO = "ORG_NO";
//    private static final String COL_CTL_TYPE = "CTL_TYPE";
//
//    public RequestRelay() {
//    }
//
//    @PostConstruct
//    public void init() {
//        this.threadPoolExecutor = new ThreadPoolExecutor(this.threadPoolProperties.getCorePoolSize(), this.threadPoolProperties.getMaximumPoolSize(), this.threadPoolProperties.getKeepAliveTime(), TimeUnit.SECONDS, new LinkedBlockingDeque(this.threadPoolProperties.getQueueSize()), new CustomizableThreadFactory("requestRelay"));
//    }
//
//    private AsyncRsp syncNotifyDevice(String url, HttpMethod httpMethod, Object params) {
//        if (!url.startsWith("http")) {
//            url = "http://".concat(url);
//        }
//
//        AsyncRsp asyncRsp = null;
//        ServerManager serverMsg = null;
//        List<String> serverMsgList = null;
//        HttpHeaders requestHeaders = new HttpHeaders();
//        requestHeaders.setContentType(MediaType.APPLICATION_JSON_UTF8);
//        requestHeaders.setAccept(Collections.emptyList());
//
//        try {
//            ResponseEntity<Object> responseEntity = null;
//            if (!HttpMethod.GET.equals(httpMethod) && !HttpMethod.DELETE.equals(httpMethod)) {
//                responseEntity = this.restTemplate.exchange(url, httpMethod, new HttpEntity(params, requestHeaders), Object.class, new Object[0]);
//            } else {
//                Map<String, String> paramsMap = (Map)params;
//                StringBuilder sb = (new StringBuilder(url)).append("?");
//                paramsMap.forEach((k, v) -> {
//                    sb.append(k).append("=").append(v).append("&");
//                });
//                responseEntity = this.restTemplate.exchange(sb.substring(0, sb.length() - 1), httpMethod, new HttpEntity(requestHeaders), Object.class, new Object[0]);
//            }
//
//            asyncRsp = new AsyncRsp("ok", responseEntity.getStatusCode().getReasonPhrase(), responseEntity.getBody());
//            HttpHeaders responseHeaders = responseEntity.getHeaders();
//            serverMsgList = responseHeaders.get("serverMsg");
//            if (!CollectionUtils.isEmpty(serverMsgList)) {
//                ObjectMapper mapper = new ObjectMapper();
//                serverMsg = (ServerManager)mapper.readValue((String)serverMsgList.get(0), ServerManager.class);
//            }
//        } catch (RestClientException var11) {
//            log.error("发送请求失败", var11);
//            asyncRsp = new AsyncRsp("fail", var11.getMessage());
//            String serviceId = url.substring(7);
//            serviceId = serviceId.indexOf("/") > 0 ? serviceId.substring(0, serviceId.indexOf("/")) : serviceId;
//            serverMsg = this.zhygLoadBalancer.choose(serviceId);
//        } catch (IOException var12) {
//            log.error("JSON转换异常：{}", serverMsgList);
//        }
//
//        asyncRsp.setServerMsg(serverMsg);
//        return asyncRsp;
//    }
//
//    private void asyncNotifyDevice(String url, HttpMethod httpMethod, Object params, RequestCallback callback) {
//        this.threadPoolExecutor.execute(() -> {
//            try {
//                AsyncRsp asyncRsp = this.syncNotifyDevice(url, httpMethod, params);
//                callback.call(asyncRsp);
//            } catch (RestClientException var6) {
//                log.error(String.format("转发功能到[%s]异常", url), var6);
//                if (this.threadPoolProperties.getRetry().isEnable()) {
//                    log.info("请求重试第1次...");
//                    this.threadPoolExecutor.execute(new RequestRelay.RetryThread(url, httpMethod, params, callback, 0));
//                }
//            }
//
//        });
//    }
//
//    public AsyncRsp syncNotifyDevice(String id, String interfaceId, HttpMethod httpMethod, Object params) {
//        log.info("Sync notify device: {id: {}, interfaceId: {}, httpMethod: {}, params: {}}", new Object[]{id, interfaceId, httpMethod.name(), params});
//        AsyncRsp asyncRsp = null;
//
//        try {
//            Map<String, Object> resultMap = this.deviceDao.queryCompanyAndOrgNoByDevMsg(id);
//            Assert.notEmpty(resultMap, String.format("未找到设备厂商信息，设备ID: [%s]", id));
//            String url = this.concatString("http", (String)resultMap.get("CTL_TYPE"), (String)resultMap.get("COMPANY"), (String)resultMap.get("ORG_NO"), interfaceId);
//            asyncRsp = this.syncNotifyDevice(url, httpMethod, params);
//        } catch (Exception var8) {
//            log.error("Sync notify device failed.", var8);
//            asyncRsp = new AsyncRsp("fail", var8.getMessage());
//        }
//
//        return asyncRsp;
//    }
//
//    public void asyncNotifyDevice(String id, String interfaceId, HttpMethod httpMethod, Object params, RequestCallback callback) {
//        log.info("Sync notify device: {id: {}, interfaceId: {}, httpMethod: {}, params: {}}", new Object[]{id, interfaceId, httpMethod.name(), params});
//
//        try {
//            Map<String, Object> resultMap = this.deviceDao.queryCompanyAndOrgNoByDevMsg(id);
//            Assert.notEmpty(resultMap, String.format("未找到设备厂商信息，设备ID: [%s]", id));
//            String url = this.concatString("http", (String)resultMap.get("CTL_TYPE"), (String)resultMap.get("COMPANY"), (String)resultMap.get("ORG_NO"), interfaceId);
//            this.asyncNotifyDevice(url, httpMethod, params, callback);
//        } catch (Exception var8) {
//            log.error("Async notify device failed.", var8);
//        }
//
//    }
//
//    public AsyncRsp syncNotifyDeviceByController(String controllerId, String interfaceId, HttpMethod httpMethod, Object params) {
//        log.info("Sync notify device by controller: {controllerId: {}, interfaceId: {}, httpMethod: {}, params: {}}", new Object[]{controllerId, interfaceId, httpMethod.name(), params});
//        AsyncRsp asyncRsp = null;
//
//        try {
//            Map<String, Object> resultMap = this.deviceDao.queryCompanyAndOrgNoByCtlMsg(controllerId);
//            Assert.notEmpty(resultMap, String.format("未找到设备控制器厂商信息，设备控制器ID: [%s]", controllerId));
//            String url = this.concatString("http", (String)resultMap.get("CTL_TYPE"), (String)resultMap.get("COMPANY"), (String)resultMap.get("ORG_NO"), interfaceId);
//            asyncRsp = this.syncNotifyDevice(url, httpMethod, params);
//        } catch (Exception var8) {
//            log.error("Sync notify device by controller failed.", var8);
//            asyncRsp = new AsyncRsp("fail", var8.getMessage());
//        }
//
//        return asyncRsp;
//    }
//
//    public void asyncNotifyDeviceByController(String controllerId, String interfaceId, HttpMethod httpMethod, Object params, RequestCallback callback) {
//        log.info("Async notify device by controller: {controllerId: {}, interfaceId: {}, httpMethod: {}, params: {}}", new Object[]{controllerId, interfaceId, httpMethod.name(), params});
//
//        try {
//            Map<String, Object> resultMap = this.deviceDao.queryCompanyAndOrgNoByCtlMsg(controllerId);
//            Assert.notEmpty(resultMap, String.format("未找到设备控制器厂商信息，设备控制器ID: [%s]", controllerId));
//            String url = this.concatString("http", (String)resultMap.get("CTL_TYPE"), (String)resultMap.get("COMPANY"), (String)resultMap.get("ORG_NO"), interfaceId);
//            this.asyncNotifyDevice(url, httpMethod, params, callback);
//        } catch (Exception var8) {
//            log.error("Async notify device by controller failed.", var8);
//        }
//
//    }
//
//    private String concatUrl(String scheme, String ip, String port, String path) {
//        if (!path.startsWith("/")) {
//            path = "/" + path;
//        }
//
//        return scheme + "://" + ip + ":" + port + path;
//    }
//
//    private String concatString(String schema, String type, String company, String orgNo, String interfaceId) {
//        return schema + "://" + type + "-" + company + "-" + orgNo + "-" + interfaceId;
//    }
//
//    class RetryThread implements Runnable {
//        private String url;
//        private HttpMethod httpMethod;
//        private Object params;
//        private RequestCallback callback;
//        private int count;
//
//        RetryThread(String url, HttpMethod httpMethod, Object params, RequestCallback callback, int count) {
//            this.url = url;
//            this.httpMethod = httpMethod;
//            this.params = params;
//            this.callback = callback;
//            this.count = count;
//        }
//
//        public void run() {
//            try {
//                AsyncRsp asyncRsp = RequestRelay.this.syncNotifyDevice(this.url, this.httpMethod, this.params);
//                this.callback.call(asyncRsp);
//            } catch (RestClientException var4) {
//                try {
//                    Thread.sleep((long)RequestRelay.this.threadPoolProperties.getRetry().getInterval() * 1000L);
//                } catch (InterruptedException var3) {
//                    RequestRelay.log.error("请求重发异常：", var3);
//                    Thread.currentThread().interrupt();
//                }
//
//                ++this.count;
//                if (this.count < RequestRelay.this.threadPoolProperties.getRetry().getMaxTimes()) {
//                    RequestRelay.log.info(String.format("请求重试第%d次...", this.count));
//                    RequestRelay.this.threadPoolExecutor.execute(RequestRelay.this.new RetryThread(this.url, this.httpMethod, this.params, this.callback, this.count));
//                }
//            }
//
//        }
//    }
//}
